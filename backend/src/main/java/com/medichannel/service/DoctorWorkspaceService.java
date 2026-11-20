package com.medichannel.service;

import com.medichannel.dto.doctor.DoctorSessionDto;
import com.medichannel.dto.doctor.DoctorSessionQueueDto;
import com.medichannel.dto.doctor.QueueAppointmentDto;
import com.medichannel.entity.Appointment;
import com.medichannel.entity.ChannelSession;
import com.medichannel.entity.Consultation;
import com.medichannel.entity.Doctor;
import com.medichannel.enums.AppointmentStatus;
import com.medichannel.enums.SessionStatus;
import com.medichannel.enums.SessionType;
import com.medichannel.event.QueueUpdatedEvent;
import com.medichannel.exception.ApiException;
import com.medichannel.repository.AppointmentRepository;
import com.medichannel.repository.ChannelSessionRepository;
import com.medichannel.repository.ConsultationRepository;
import com.medichannel.repository.DoctorRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DoctorWorkspaceService {

    private final DoctorRepository doctorRepository;
    private final ChannelSessionRepository sessionRepository;
    private final AppointmentRepository appointmentRepository;
    private final ConsultationRepository consultationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public DoctorWorkspaceService(
            DoctorRepository doctorRepository,
            ChannelSessionRepository sessionRepository,
            AppointmentRepository appointmentRepository,
            ConsultationRepository consultationRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.doctorRepository = doctorRepository;
        this.sessionRepository = sessionRepository;
        this.appointmentRepository = appointmentRepository;
        this.consultationRepository = consultationRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<DoctorSessionDto> listMySessions(UUID doctorUserId, LocalDate date, boolean all) {
        Doctor doctor = resolveDoctorByUserId(doctorUserId);

        List<ChannelSession> sessions;
        if (all) {
            sessions = sessionRepository.findByDoctorIdOrderBySessionDateDescStartTimeAsc(doctor.getId());
        } else {
            LocalDate targetDate = date != null ? date : LocalDate.now(ChannelSessionService.COLOMBO_ZONE);
            sessions = sessionRepository.findByDoctorIdAndSessionDateOrderByStartTimeAsc(doctor.getId(), targetDate);
        }

        return sessions.stream()
                .map(session -> toDoctorSessionDto(session, appointmentRepository.findQueueBySessionId(session.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public DoctorSessionQueueDto getSessionQueue(UUID doctorUserId, UUID sessionId) {
        ChannelSession session = loadOwnedSession(doctorUserId, sessionId);
        return buildQueueResponse(session);
    }

    @CacheEvict(value = {"publicDoctors", "publicSpecialisations"}, allEntries = true)
    @Transactional
    public DoctorSessionQueueDto startSession(UUID doctorUserId, UUID sessionId) {
        ChannelSession session = loadOwnedSession(doctorUserId, sessionId);

        if (session.getStatus() != SessionStatus.SCHEDULED && session.getStatus() != SessionStatus.OPEN) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Only SCHEDULED or OPEN sessions can be started (current status: " + session.getStatus() + ")"
            );
        }

        session.setStatus(SessionStatus.IN_PROGRESS);
        ChannelSession saved = sessionRepository.save(session);

        eventPublisher.publishEvent(new QueueUpdatedEvent(
                saved.getId(),
                QueueUpdatedEvent.ActionType.SESSION_STARTED,
                saved.getStatus(),
                saved.getSessionType(),
                saved.getNowServing(),
                null,
                null,
                null,
                null
        ));

        return buildQueueResponse(saved);
    }

    @Transactional
    public DoctorSessionQueueDto callNextPatient(UUID doctorUserId, UUID sessionId) {
        ChannelSession session = loadOwnedSession(doctorUserId, sessionId);
        ensureSessionInProgress(session);

        List<Appointment> queue = appointmentRepository.findQueueBySessionId(session.getId());
        ensureNoActiveConsultation(queue);

        AppointmentStatus requiredStatus = requiredReadyStatus(session.getSessionType());
        Appointment next = queue.stream()
                .filter(a -> a.getStatus() == requiredStatus)
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        session.getSessionType() == SessionType.PHYSICAL
                                ? "No arrived patients waiting in the queue"
                                : "No booked patients waiting for online consultation"
                ));

        return activatePatientConsultation(session, next, queue);
    }

    @Transactional
    public DoctorSessionQueueDto callAppointment(UUID doctorUserId, UUID sessionId, UUID appointmentId) {
        ChannelSession session = loadOwnedSession(doctorUserId, sessionId);
        ensureSessionInProgress(session);

        List<Appointment> queue = appointmentRepository.findQueueBySessionId(session.getId());
        ensureNoActiveConsultation(queue);

        Appointment target = queue.stream()
                .filter(a -> a.getId().equals(appointmentId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Appointment not found in this session"));

        AppointmentStatus requiredStatus = requiredReadyStatus(session.getSessionType());
        if (target.getStatus() != requiredStatus) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    session.getSessionType() == SessionType.PHYSICAL
                            ? "Physical appointments must be marked ARRIVED at reception before being called"
                            : "Online appointments must be in BOOKED status to start consultation"
            );
        }

        return activatePatientConsultation(session, target, queue);
    }

    @Transactional
    public DoctorSessionQueueDto markCompleted(UUID doctorUserId, UUID sessionId, UUID appointmentId) {
        ChannelSession session = loadOwnedSession(doctorUserId, sessionId);
        ensureSessionInProgress(session);

        Appointment appointment = loadSessionAppointment(session, appointmentId);
        if (appointment.getStatus() != AppointmentStatus.IN_CONSULTATION) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Only a patient currently IN_CONSULTATION can be marked COMPLETED"
            );
        }

        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointmentRepository.save(appointment);

        lockConsultationIfPresent(appointment.getId());

        eventPublisher.publishEvent(new QueueUpdatedEvent(
                session.getId(),
                QueueUpdatedEvent.ActionType.PATIENT_COMPLETED,
                session.getStatus(),
                session.getSessionType(),
                session.getNowServing(),
                appointment.getId(),
                appointment.getStatus(),
                extractPatientUserId(appointment),
                null
        ));

        return buildQueueResponse(session);
    }

    @Transactional
    public DoctorSessionQueueDto markNoShow(UUID doctorUserId, UUID sessionId, UUID appointmentId) {
        ChannelSession session = loadOwnedSession(doctorUserId, sessionId);
        ensureSessionInProgress(session);

        Appointment appointment = loadSessionAppointment(session, appointmentId);
        if (appointment.getStatus() != AppointmentStatus.BOOKED
                && appointment.getStatus() != AppointmentStatus.ARRIVED
                && appointment.getStatus() != AppointmentStatus.IN_CONSULTATION) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot mark appointment as NO_SHOW from status " + appointment.getStatus()
            );
        }

        appointment.setStatus(AppointmentStatus.NO_SHOW);
        appointmentRepository.save(appointment);

        lockConsultationIfPresent(appointment.getId());

        eventPublisher.publishEvent(new QueueUpdatedEvent(
                session.getId(),
                QueueUpdatedEvent.ActionType.PATIENT_NO_SHOW,
                session.getStatus(),
                session.getSessionType(),
                session.getNowServing(),
                appointment.getId(),
                appointment.getStatus(),
                extractPatientUserId(appointment),
                null
        ));

        return buildQueueResponse(session);
    }

    @CacheEvict(value = {"publicDoctors", "publicSpecialisations"}, allEntries = true)
    @Transactional
    public DoctorSessionQueueDto endSession(UUID doctorUserId, UUID sessionId) {
        ChannelSession session = loadOwnedSession(doctorUserId, sessionId);
        ensureSessionInProgress(session);

        List<Appointment> queue = appointmentRepository.findQueueBySessionId(session.getId());
        boolean hasInConsultation = queue.stream()
                .anyMatch(a -> a.getStatus() == AppointmentStatus.IN_CONSULTATION);
        if (hasInConsultation) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Please mark the current patient COMPLETED or NO_SHOW before ending the session"
            );
        }

        session.setStatus(SessionStatus.COMPLETED);
        ChannelSession saved = sessionRepository.save(session);

        eventPublisher.publishEvent(new QueueUpdatedEvent(
                saved.getId(),
                QueueUpdatedEvent.ActionType.SESSION_ENDED,
                saved.getStatus(),
                saved.getSessionType(),
                saved.getNowServing(),
                null,
                null,
                null,
                null
        ));

        return buildQueueResponse(saved);
    }

    // ==================== HELPERS ====================

    private DoctorSessionQueueDto activatePatientConsultation(
            ChannelSession session,
            Appointment target,
            List<Appointment> queue
    ) {
        target.setStatus(AppointmentStatus.IN_CONSULTATION);
        appointmentRepository.save(target);

        session.setNowServing(target.getAppointmentNumber());
        ChannelSession savedSession = sessionRepository.save(session);

        // BR-09: Open consultation record when the doctor starts this appointment
        if (consultationRepository.findByAppointmentId(target.getId()).isEmpty()) {
            Consultation consultation = new Consultation();
            consultation.setAppointment(target);
            consultation.setDoctor(session.getDoctor());
            consultation.setReadOnly(false);
            consultation.setStartedAt(Instant.now());
            consultationRepository.save(consultation);
        }

        int threeAwayNumber = target.getAppointmentNumber() + 3;
        UUID threeAwayUserId = queue.stream()
                .filter(a -> a.getAppointmentNumber() == threeAwayNumber)
                .filter(a -> a.getStatus() == AppointmentStatus.BOOKED || a.getStatus() == AppointmentStatus.ARRIVED)
                .map(this::extractPatientUserId)
                .findFirst()
                .orElse(null);

        eventPublisher.publishEvent(new QueueUpdatedEvent(
                savedSession.getId(),
                QueueUpdatedEvent.ActionType.PATIENT_CALLED,
                savedSession.getStatus(),
                savedSession.getSessionType(),
                savedSession.getNowServing(),
                target.getId(),
                target.getStatus(),
                extractPatientUserId(target),
                threeAwayUserId
        ));

        return buildQueueResponse(savedSession);
    }

    private void lockConsultationIfPresent(UUID appointmentId) {
        consultationRepository.findByAppointmentId(appointmentId).ifPresent(consultation -> {
            if (!consultation.isReadOnly()) {
                consultation.setReadOnly(true);
                consultation.setEndedAt(Instant.now());
                consultationRepository.save(consultation);
            }
        });
    }

    private Doctor resolveDoctorByUserId(UUID userId) {
        return doctorRepository.findWithDetailsByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor profile not found for this account"));
    }

    private ChannelSession loadOwnedSession(UUID doctorUserId, UUID sessionId) {
        ChannelSession session = sessionRepository.findWithDetailsById(sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Session not found"));
        if (!session.getDoctor().getUser().getId().equals(doctorUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only access and manage your own sessions");
        }
        return session;
    }

    private Appointment loadSessionAppointment(ChannelSession session, UUID appointmentId) {
        Appointment appointment = appointmentRepository.findWithDetailsById(appointmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Appointment not found"));
        if (!appointment.getSession().getId().equals(session.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Appointment does not belong to this session");
        }
        return appointment;
    }

    private void ensureSessionInProgress(ChannelSession session) {
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Session must be IN_PROGRESS to perform queue actions (current status: " + session.getStatus() + ")"
            );
        }
    }

    private void ensureNoActiveConsultation(List<Appointment> queue) {
        boolean alreadyInConsultation = queue.stream()
                .anyMatch(a -> a.getStatus() == AppointmentStatus.IN_CONSULTATION);
        if (alreadyInConsultation) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Another patient is already IN_CONSULTATION; mark them COMPLETED or NO_SHOW first"
            );
        }
    }

    private AppointmentStatus requiredReadyStatus(SessionType type) {
        return type == SessionType.PHYSICAL ? AppointmentStatus.ARRIVED : AppointmentStatus.BOOKED;
    }

    private UUID extractPatientUserId(Appointment appointment) {
        if (appointment.getPatient() == null || appointment.getPatient().getUser() == null) {
            return null;
        }
        return appointment.getPatient().getUser().getId();
    }

    private DoctorSessionQueueDto buildQueueResponse(ChannelSession session) {
        List<Appointment> queue = appointmentRepository.findQueueBySessionId(session.getId());
        List<UUID> appointmentIds = queue.stream().map(Appointment::getId).toList();

        Map<UUID, Consultation> consultationByAppointment = appointmentIds.isEmpty()
                ? Map.of()
                : consultationRepository.findByAppointmentIdIn(appointmentIds).stream()
                        .collect(Collectors.toMap(c -> c.getAppointment().getId(), Function.identity()));

        List<QueueAppointmentDto> items = queue.stream()
                .map(a -> {
                    Consultation consultation = consultationByAppointment.get(a.getId());
                    return new QueueAppointmentDto(
                            a.getId(),
                            a.getAppointmentNumber(),
                            a.getBookingReference(),
                            a.getPatient().getId(),
                            a.getPatient().getUser().getFullName(),
                            a.getAppointmentType(),
                            a.getStatus(),
                            a.getPaymentStatus(),
                            a.getEstimatedTime(),
                            a.getReasonForVisit(),
                            consultation != null,
                            consultation != null && consultation.isReadOnly()
                    );
                })
                .toList();

        return new DoctorSessionQueueDto(toDoctorSessionDto(session, queue), items);
    }

    private DoctorSessionDto toDoctorSessionDto(ChannelSession session, List<Appointment> queue) {
        int activeBookings = 0;
        int arrivedCount = 0;
        int completedCount = 0;
        int noShowCount = 0;
        UUID currentAppointmentId = null;

        for (Appointment a : queue) {
            if (a.getStatus() != AppointmentStatus.CANCELLED) {
                activeBookings++;
            }
            if (a.getStatus() == AppointmentStatus.ARRIVED) {
                arrivedCount++;
            } else if (a.getStatus() == AppointmentStatus.COMPLETED) {
                completedCount++;
            } else if (a.getStatus() == AppointmentStatus.NO_SHOW) {
                noShowCount++;
            } else if (a.getStatus() == AppointmentStatus.IN_CONSULTATION) {
                currentAppointmentId = a.getId();
            }
        }

        return new DoctorSessionDto(
                session.getId(),
                session.getSessionDate(),
                session.getStartTime(),
                session.getEndTime(),
                session.getSessionType(),
                session.getRoom() == null ? null : session.getRoom().getName(),
                session.getRoom() == null ? null : session.getRoom().getLocation(),
                session.getMaxPatients(),
                session.getMinutesPerPatient(),
                session.getFee(),
                session.getDelayMinutes(),
                session.getStatus(),
                session.getNowServing(),
                activeBookings,
                arrivedCount,
                completedCount,
                noShowCount,
                currentAppointmentId
        );
    }
}