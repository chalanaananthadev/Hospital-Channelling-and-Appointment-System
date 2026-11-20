package com.medichannel.service;

import com.medichannel.dto.common.PageResponse;
import com.medichannel.dto.session.PublicSessionDto;
import com.medichannel.dto.session.SessionCreateRequest;
import com.medichannel.dto.session.SessionDelayRequest;
import com.medichannel.dto.session.SessionResponse;
import com.medichannel.dto.session.SessionUpdateRequest;
import com.medichannel.entity.Appointment;
import com.medichannel.entity.ChannelSession;
import com.medichannel.entity.Doctor;
import com.medichannel.entity.Room;
import com.medichannel.entity.UserAccount;
import com.medichannel.enums.AppointmentStatus;
import com.medichannel.enums.AuditAction;
import com.medichannel.enums.SessionStatus;
import com.medichannel.enums.SessionType;
import com.medichannel.event.SessionChangedEvent;
import com.medichannel.exception.ApiException;
import com.medichannel.repository.AppointmentRepository;
import com.medichannel.repository.ChannelSessionRepository;
import com.medichannel.repository.DoctorRepository;
import com.medichannel.repository.RoomRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ChannelSessionService {

    public static final ZoneId COLOMBO_ZONE = ZoneId.of("Asia/Colombo");

    private final ChannelSessionRepository sessionRepository;
    private final DoctorRepository doctorRepository;
    private final RoomRepository roomRepository;
    private final AppointmentRepository appointmentRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public ChannelSessionService(
            ChannelSessionRepository sessionRepository,
            DoctorRepository doctorRepository,
            RoomRepository roomRepository,
            AppointmentRepository appointmentRepository,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.sessionRepository = sessionRepository;
        this.doctorRepository = doctorRepository;
        this.roomRepository = roomRepository;
        this.appointmentRepository = appointmentRepository;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    // ==================== MANAGER OPERATIONS ====================

    @Transactional(readOnly = true)
    public PageResponse<SessionResponse> listManagerSessions(
            UUID doctorId,
            UUID specialisationId,
            LocalDate date,
            SessionType sessionType,
            SessionStatus status,
            String search,
            int page,
            int size
    ) {
        String needle = (search == null || search.isBlank()) ? null : search.trim().toLowerCase(Locale.ROOT);

        List<SessionResponse> filtered = sessionRepository.findAllByOrderBySessionDateDescStartTimeAsc().stream()
                .filter(s -> doctorId == null || s.getDoctor().getId().equals(doctorId))
                .filter(s -> specialisationId == null || s.getDoctor().getSpecialisation().getId().equals(specialisationId))
                .filter(s -> date == null || s.getSessionDate().equals(date))
                .filter(s -> sessionType == null || s.getSessionType() == sessionType)
                .filter(s -> status == null || s.getStatus() == status)
                .map(this::toSessionResponse)
                .filter(s -> needle == null
                        || containsIgnoreCase(s.doctorName(), needle)
                        || containsIgnoreCase(s.specialisationName(), needle)
                        || containsIgnoreCase(s.roomName(), needle))
                .toList();

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int total = filtered.size();
        int fromIndex = Math.min(safePage * safeSize, total);
        int toIndex = Math.min(fromIndex + safeSize, total);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);

        return new PageResponse<>(
                List.copyOf(filtered.subList(fromIndex, toIndex)),
                safePage,
                safeSize,
                total,
                totalPages
        );
    }

    @Transactional(readOnly = true)
    public SessionResponse getManagerSession(UUID id) {
        ChannelSession session = sessionRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Session not found"));
        return toSessionResponse(session);
    }

    @CacheEvict(value = {"publicDoctors", "publicSpecialisations"}, allEntries = true)
    @Transactional
    public SessionResponse createSession(SessionCreateRequest request) {
        validateScheduleTimes(request.sessionDate(), request.startTime(), request.endTime());

        SessionStatus initialStatus = request.status() == null ? SessionStatus.SCHEDULED : request.status();
        if (initialStatus != SessionStatus.SCHEDULED && initialStatus != SessionStatus.OPEN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New sessions can only start with SCHEDULED or OPEN status");
        }

        Doctor doctor = doctorRepository.findWithDetailsById(request.doctorId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor not found"));
        if (!doctor.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot schedule a session for an inactive doctor");
        }

        Room room = resolveAndValidateRoom(request.sessionType(), request.roomId(), null);

        enforceNoClashes(
                doctor.getId(),
                room == null ? null : room.getId(),
                request.sessionDate(),
                request.startTime(),
                request.endTime(),
                null
        );

        ChannelSession session = new ChannelSession();
        session.setDoctor(doctor);
        session.setRoom(room);
        session.setSessionDate(request.sessionDate());
        session.setStartTime(request.startTime());
        session.setEndTime(request.endTime());
        session.setSessionType(request.sessionType());
        session.setMaxPatients(request.maxPatients());
        session.setMinutesPerPatient(request.minutesPerPatient());
        session.setFee(request.fee());
        session.setDelayMinutes(0);
        session.setStatus(initialStatus);

        ChannelSession saved = sessionRepository.save(session);
        SessionResponse response = toSessionResponse(saved);

        eventPublisher.publishEvent(new SessionChangedEvent(
                saved.getId(),
                SessionChangedEvent.ChangeType.CREATED,
                saved.getStatus(),
                saved.getDelayMinutes(),
                response.numbersLeft(),
                List.of()
        ));

        return response;
    }

    @CacheEvict(value = {"publicDoctors", "publicSpecialisations"}, allEntries = true)
    @Transactional
    public SessionResponse updateSession(UUID id, SessionUpdateRequest request) {
        ChannelSession session = sessionRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Session not found"));

        if (session.getStatus() != SessionStatus.SCHEDULED && session.getStatus() != SessionStatus.OPEN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only SCHEDULED or OPEN sessions can be edited");
        }

        validateScheduleTimes(request.sessionDate(), request.startTime(), request.endTime());

        int highestIssuedNumber = appointmentRepository.findMaxNumberBySessionId(id);
        if (request.maxPatients() < highestIssuedNumber) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Maximum patients cannot be lower than already issued appointment number (" + highestIssuedNumber + ")"
            );
        }
        if (highestIssuedNumber > 0 && request.sessionType() != session.getSessionType()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot change session type after appointments have already been booked"
            );
        }

        Doctor doctor = doctorRepository.findWithDetailsById(request.doctorId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor not found"));
        boolean doctorChanged = !doctor.getId().equals(session.getDoctor().getId());
        if (doctorChanged && !doctor.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot assign an inactive doctor");
        }

        UUID existingRoomId = session.getRoom() == null ? null : session.getRoom().getId();
        Room room = resolveAndValidateRoom(request.sessionType(), request.roomId(), existingRoomId);

        enforceNoClashes(
                doctor.getId(),
                room == null ? null : room.getId(),
                request.sessionDate(),
                request.startTime(),
                request.endTime(),
                id
        );

        boolean timingChanged = !session.getStartTime().equals(request.startTime())
                || session.getMinutesPerPatient() != request.minutesPerPatient();

        session.setDoctor(doctor);
        session.setRoom(room);
        session.setSessionDate(request.sessionDate());
        session.setStartTime(request.startTime());
        session.setEndTime(request.endTime());
        session.setSessionType(request.sessionType());
        session.setMaxPatients(request.maxPatients());
        session.setMinutesPerPatient(request.minutesPerPatient());
        session.setFee(request.fee());

        ChannelSession saved = sessionRepository.save(session);

        List<UUID> affectedIds = List.of();
        if (timingChanged) {
            affectedIds = recalculateAppointmentTimes(saved);
        }

        SessionResponse response = toSessionResponse(saved);
        eventPublisher.publishEvent(new SessionChangedEvent(
                saved.getId(),
                SessionChangedEvent.ChangeType.UPDATED,
                saved.getStatus(),
                saved.getDelayMinutes(),
                response.numbersLeft(),
                affectedIds
        ));

        return response;
    }

    @CacheEvict(value = {"publicDoctors", "publicSpecialisations"}, allEntries = true)
    @Transactional
    public SessionResponse updateSessionStatus(UUID id, SessionStatus newStatus) {
        ChannelSession session = sessionRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Session not found"));

        if (newStatus != SessionStatus.SCHEDULED && newStatus != SessionStatus.OPEN) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Managers can only switch session status between SCHEDULED and OPEN; use the cancel endpoint to cancel"
            );
        }
        if (session.getStatus() != SessionStatus.SCHEDULED && session.getStatus() != SessionStatus.OPEN) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot change status of a session that is " + session.getStatus()
            );
        }

        session.setStatus(newStatus);
        ChannelSession saved = sessionRepository.save(session);
        SessionResponse response = toSessionResponse(saved);

        eventPublisher.publishEvent(new SessionChangedEvent(
                saved.getId(),
                SessionChangedEvent.ChangeType.STATUS_CHANGED,
                saved.getStatus(),
                saved.getDelayMinutes(),
                response.numbersLeft(),
                List.of()
        ));

        return response;
    }

    @CacheEvict(value = {"publicDoctors", "publicSpecialisations"}, allEntries = true)
    @Transactional
    public SessionResponse delaySession(UUID id, SessionDelayRequest request) {
        ChannelSession session = sessionRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Session not found"));

        if (session.getStatus() == SessionStatus.COMPLETED || session.getStatus() == SessionStatus.CANCELLED) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot delay a session that is " + session.getStatus()
            );
        }

        session.setDelayMinutes(request.delayMinutes());
        ChannelSession saved = sessionRepository.save(session);

        List<UUID> affectedIds = recalculateAppointmentTimes(saved);
        SessionResponse response = toSessionResponse(saved);

        eventPublisher.publishEvent(new SessionChangedEvent(
                saved.getId(),
                SessionChangedEvent.ChangeType.DELAYED,
                saved.getStatus(),
                saved.getDelayMinutes(),
                response.numbersLeft(),
                affectedIds
        ));

        return response;
    }

    @CacheEvict(value = {"publicDoctors", "publicSpecialisations"}, allEntries = true)
    @Transactional
    public SessionResponse cancelSession(UUID id, UserAccount actor, String ipAddress) {
        ChannelSession session = sessionRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Session not found"));

        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new ApiException(HttpStatus.CONFLICT, "Session is already cancelled");
        }
        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Completed sessions cannot be cancelled");
        }

        session.setStatus(SessionStatus.CANCELLED);
        ChannelSession saved = sessionRepository.save(session);

        Instant now = Instant.now();
        List<Appointment> appointments = appointmentRepository.findBySessionIdOrderByAppointmentNumberAsc(id);
        List<Appointment> updatedAppointments = new ArrayList<>();
        List<UUID> affectedIds = new ArrayList<>();

        for (Appointment appointment : appointments) {
            if (appointment.getStatus() != AppointmentStatus.CANCELLED
                    && appointment.getStatus() != AppointmentStatus.COMPLETED) {
                appointment.setStatus(AppointmentStatus.CANCELLED);
                appointment.setCancelledAt(now);
                updatedAppointments.add(appointment);
                if (appointment.getPatient() != null && appointment.getPatient().getId() != null) {
                    affectedIds.add(appointment.getPatient().getId());
                }
            }
        }
        if (!updatedAppointments.isEmpty()) {
            appointmentRepository.saveAll(updatedAppointments);
        }

        auditService.record(actor, AuditAction.SESSION_CANCELLED, "ChannelSession", saved.getId(), ipAddress);

        eventPublisher.publishEvent(new SessionChangedEvent(
                saved.getId(),
                SessionChangedEvent.ChangeType.CANCELLED,
                saved.getStatus(),
                saved.getDelayMinutes(),
                0,
                affectedIds
        ));

        return toSessionResponse(saved);
    }

    // ==================== PUBLIC SEARCH ====================

    @Transactional(readOnly = true)
    public List<PublicSessionDto> searchPublicSessions(
            UUID doctorId,
            UUID specialisationId,
            String doctorName,
            LocalDate date,
            SessionType sessionType
    ) {
        LocalDate fromDate = date != null ? date : LocalDate.now(COLOMBO_ZONE);
        String needle = (doctorName == null || doctorName.isBlank()) ? null : doctorName.trim().toLowerCase(Locale.ROOT);

        return sessionRepository.findBySessionDateGreaterThanEqualOrderBySessionDateAscStartTimeAsc(fromDate).stream()
                .filter(s -> s.getStatus() == SessionStatus.SCHEDULED
                        || s.getStatus() == SessionStatus.OPEN
                        || s.getStatus() == SessionStatus.IN_PROGRESS)
                .filter(s -> s.getDoctor().isActive())
                .filter(s -> doctorId == null || s.getDoctor().getId().equals(doctorId))
                .filter(s -> specialisationId == null || s.getDoctor().getSpecialisation().getId().equals(specialisationId))
                .filter(s -> date == null || s.getSessionDate().equals(date))
                .filter(s -> sessionType == null || s.getSessionType() == sessionType)
                .map(this::toPublicSessionDto)
                .filter(s -> needle == null || containsIgnoreCase(s.doctorName(), needle))
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicSessionDto getPublicSession(UUID id) {
        ChannelSession session = sessionRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Session not found"));
        return toPublicSessionDto(session);
    }

    // ==================== BUSINESS RULE HELPERS ====================

    /**
     * BR-06: Estimated time = start time + (number - 1) * minutes per patient + session delay.
     */
    public LocalTime calculateEstimatedTime(
            LocalTime startTime,
            int appointmentNumber,
            int minutesPerPatient,
            int delayMinutes
    ) {
        long offsetMinutes = (long) Math.max(appointmentNumber - 1, 0) * minutesPerPatient + delayMinutes;
        return startTime.plusMinutes(offsetMinutes);
    }

    private void validateScheduleTimes(LocalDate sessionDate, LocalTime startTime, LocalTime endTime) {
        LocalDate todayColombo = LocalDate.now(COLOMBO_ZONE);
        if (sessionDate.isBefore(todayColombo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Session date cannot be in the past");
        }
        if (!startTime.isBefore(endTime)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Start time must be earlier than end time");
        }
    }

    /**
     * BR-01: Physical sessions require an active room; online sessions must not specify a room.
     */
    private Room resolveAndValidateRoom(SessionType sessionType, UUID roomId, UUID existingRoomId) {
        if (sessionType == SessionType.ONLINE) {
            if (roomId != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Online sessions must not be assigned a physical room");
            }
            return null;
        }

        if (roomId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Physical sessions require a room");
        }
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Room not found"));
        boolean roomChanged = !room.getId().equals(existingRoomId);
        if (roomChanged && !room.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot assign an inactive room");
        }
        return room;
    }

    /**
     * BR-02: Prevent overlapping sessions for the same doctor or same room.
     */
    private void enforceNoClashes(
            UUID doctorId,
            UUID roomId,
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            UUID excludeSessionId
    ) {
        if (sessionRepository.countDoctorOverlaps(doctorId, sessionDate, startTime, endTime, excludeSessionId) > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Doctor already has an overlapping session on " + sessionDate + " between " + startTime + " and " + endTime
            );
        }
        if (roomId != null
                && sessionRepository.countRoomOverlaps(roomId, sessionDate, startTime, endTime, excludeSessionId) > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Selected room is already booked for an overlapping session on " + sessionDate
            );
        }
    }

    private List<UUID> recalculateAppointmentTimes(ChannelSession session) {
        List<Appointment> appointments = appointmentRepository.findBySessionIdOrderByAppointmentNumberAsc(session.getId());
        List<Appointment> toSave = new ArrayList<>();
        List<UUID> affectedIds = new ArrayList<>();

        for (Appointment appointment : appointments) {
            if (appointment.getStatus() != AppointmentStatus.CANCELLED
                    && appointment.getStatus() != AppointmentStatus.COMPLETED
                    && appointment.getStatus() != AppointmentStatus.NO_SHOW) {
                appointment.setEstimatedTime(calculateEstimatedTime(
                        session.getStartTime(),
                        appointment.getAppointmentNumber(),
                        session.getMinutesPerPatient(),
                        session.getDelayMinutes()
                ));
                toSave.add(appointment);
                if (appointment.getPatient() != null && appointment.getPatient().getId() != null) {
                    affectedIds.add(appointment.getPatient().getId());
                }
            }
        }
        if (!toSave.isEmpty()) {
            appointmentRepository.saveAll(toSave);
        }
        return affectedIds;
    }

    private int computeNumbersLeft(ChannelSession session) {
        if (session.getStatus() == SessionStatus.CANCELLED || session.getStatus() == SessionStatus.COMPLETED) {
            return 0;
        }
        int highestIssued = appointmentRepository.findMaxNumberBySessionId(session.getId());
        return Math.max(0, session.getMaxPatients() - highestIssued);
    }

    private boolean containsIgnoreCase(String value, String lowerNeedle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerNeedle);
    }

    private SessionResponse toSessionResponse(ChannelSession s) {
        int bookedCount = (int) appointmentRepository.countActiveBySessionId(s.getId());
        int numbersLeft = computeNumbersLeft(s);
        return new SessionResponse(
                s.getId(),
                s.getDoctor().getId(),
                s.getDoctor().getUser().getFullName(),
                s.getDoctor().getSlmcNumber(),
                s.getDoctor().getSpecialisation().getId(),
                s.getDoctor().getSpecialisation().getName(),
                s.getRoom() == null ? null : s.getRoom().getId(),
                s.getRoom() == null ? null : s.getRoom().getName(),
                s.getRoom() == null ? null : s.getRoom().getLocation(),
                s.getSessionDate(),
                s.getStartTime(),
                s.getEndTime(),
                s.getSessionType(),
                s.getMaxPatients(),
                s.getMinutesPerPatient(),
                s.getFee(),
                s.getDelayMinutes(),
                s.getStatus(),
                s.getNowServing(),
                bookedCount,
                numbersLeft,
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }

    private PublicSessionDto toPublicSessionDto(ChannelSession s) {
        return new PublicSessionDto(
                s.getId(),
                s.getDoctor().getId(),
                s.getDoctor().getUser().getFullName(),
                s.getDoctor().getQualifications(),
                s.getDoctor().getSpecialisation().getId(),
                s.getDoctor().getSpecialisation().getName(),
                s.getRoom() == null ? null : s.getRoom().getName(),
                s.getRoom() == null ? null : s.getRoom().getLocation(),
                s.getSessionDate(),
                s.getStartTime(),
                s.getEndTime(),
                s.getSessionType(),
                s.getMaxPatients(),
                s.getMinutesPerPatient(),
                s.getFee(),
                s.getDelayMinutes(),
                s.getStatus(),
                s.getNowServing(),
                computeNumbersLeft(s)
        );
    }
}