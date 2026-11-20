package com.medichannel.service;

import com.medichannel.dto.session.SessionCreateRequest;
import com.medichannel.dto.session.SessionDelayRequest;
import com.medichannel.dto.session.SessionResponse;
import com.medichannel.entity.Appointment;
import com.medichannel.entity.ChannelSession;
import com.medichannel.entity.Doctor;
import com.medichannel.entity.Room;
import com.medichannel.entity.Specialisation;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelSessionServiceTest {

    @Mock
    private ChannelSessionRepository sessionRepository;
    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ChannelSessionService service;

    @BeforeEach
    void setUp() {
        service = new ChannelSessionService(
                sessionRepository,
                doctorRepository,
                roomRepository,
                appointmentRepository,
                auditService,
                eventPublisher
        );
    }

    @Test
    void createPhysicalSessionRequiresRoom_BR01() {
        Doctor doctor = sampleDoctor();
        when(doctorRepository.findWithDetailsById(doctor.getId())).thenReturn(Optional.of(doctor));

        SessionCreateRequest request = new SessionCreateRequest(
                doctor.getId(),
                null,
                LocalDate.now(ChannelSessionService.COLOMBO_ZONE).plusDays(1),
                LocalTime.of(9, 0),
                LocalTime.of(11, 0),
                SessionType.PHYSICAL,
                20,
                10,
                new BigDecimal("3500.00"),
                SessionStatus.OPEN
        );

        ApiException ex = assertThrows(ApiException.class, () -> service.createSession(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void createOnlineSessionRejectsRoom_BR01() {
        Doctor doctor = sampleDoctor();
        when(doctorRepository.findWithDetailsById(doctor.getId())).thenReturn(Optional.of(doctor));

        SessionCreateRequest request = new SessionCreateRequest(
                doctor.getId(),
                UUID.randomUUID(),
                LocalDate.now(ChannelSessionService.COLOMBO_ZONE).plusDays(1),
                LocalTime.of(14, 0),
                LocalTime.of(16, 0),
                SessionType.ONLINE,
                15,
                15,
                new BigDecimal("2500.00"),
                SessionStatus.OPEN
        );

        ApiException ex = assertThrows(ApiException.class, () -> service.createSession(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void createSessionRejectsDoctorOverlapWith409_BR02() {
        Doctor doctor = sampleDoctor();
        Room room = sampleRoom();
        LocalDate tomorrow = LocalDate.now(ChannelSessionService.COLOMBO_ZONE).plusDays(1);

        when(doctorRepository.findWithDetailsById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(sessionRepository.countDoctorOverlaps(
                doctor.getId(), tomorrow, LocalTime.of(9, 30), LocalTime.of(11, 30), null
        )).thenReturn(1L);

        SessionCreateRequest request = new SessionCreateRequest(
                doctor.getId(),
                room.getId(),
                tomorrow,
                LocalTime.of(9, 30),
                LocalTime.of(11, 30),
                SessionType.PHYSICAL,
                20,
                10,
                new BigDecimal("3500.00"),
                SessionStatus.OPEN
        );

        ApiException ex = assertThrows(ApiException.class, () -> service.createSession(request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void createSessionRejectsRoomOverlapWith409_BR02() {
        Doctor doctor = sampleDoctor();
        Room room = sampleRoom();
        LocalDate tomorrow = LocalDate.now(ChannelSessionService.COLOMBO_ZONE).plusDays(1);

        when(doctorRepository.findWithDetailsById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(sessionRepository.countDoctorOverlaps(any(), any(), any(), any(), any())).thenReturn(0L);
        when(sessionRepository.countRoomOverlaps(
                room.getId(), tomorrow, LocalTime.of(9, 0), LocalTime.of(11, 0), null
        )).thenReturn(1L);

        SessionCreateRequest request = new SessionCreateRequest(
                doctor.getId(),
                room.getId(),
                tomorrow,
                LocalTime.of(9, 0),
                LocalTime.of(11, 0),
                SessionType.PHYSICAL,
                20,
                10,
                new BigDecimal("3500.00"),
                SessionStatus.OPEN
        );

        ApiException ex = assertThrows(ApiException.class, () -> service.createSession(request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void createOnlineSessionSucceedsWithoutRoom() {
        Doctor doctor = sampleDoctor();
        LocalDate tomorrow = LocalDate.now(ChannelSessionService.COLOMBO_ZONE).plusDays(1);

        when(doctorRepository.findWithDetailsById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(sessionRepository.countDoctorOverlaps(any(), any(), any(), any(), any())).thenReturn(0L);
        when(sessionRepository.save(any(ChannelSession.class))).thenAnswer(inv -> {
            ChannelSession s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(appointmentRepository.countActiveBySessionId(any())).thenReturn(0L);
        when(appointmentRepository.findMaxNumberBySessionId(any())).thenReturn(0);

        SessionCreateRequest request = new SessionCreateRequest(
                doctor.getId(),
                null,
                tomorrow,
                LocalTime.of(18, 0),
                LocalTime.of(20, 0),
                SessionType.ONLINE,
                12,
                10,
                new BigDecimal("2500.00"),
                null
        );

        SessionResponse response = service.createSession(request);

        assertEquals(SessionStatus.SCHEDULED, response.status());
        assertNull(response.roomId());
        assertEquals(12, response.numbersLeft());
        verify(eventPublisher).publishEvent(any(SessionChangedEvent.class));
    }

    @Test
    void delaySessionRecalculatesActiveAppointmentTimes_BR06() {
        ChannelSession session = sampleSession();
        session.setStartTime(LocalTime.of(9, 0));
        session.setMinutesPerPatient(10);

        Appointment appt1 = new Appointment();
        appt1.setAppointmentNumber(1);
        appt1.setStatus(AppointmentStatus.BOOKED);
        appt1.setEstimatedTime(LocalTime.of(9, 0));

        Appointment appt3 = new Appointment();
        appt3.setAppointmentNumber(3);
        appt3.setStatus(AppointmentStatus.ARRIVED);
        appt3.setEstimatedTime(LocalTime.of(9, 20));

        Appointment cancelled = new Appointment();
        cancelled.setAppointmentNumber(2);
        cancelled.setStatus(AppointmentStatus.CANCELLED);
        cancelled.setEstimatedTime(LocalTime.of(9, 10));

        when(sessionRepository.findWithDetailsById(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ChannelSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appointmentRepository.findBySessionIdOrderByAppointmentNumberAsc(session.getId()))
                .thenReturn(List.of(appt1, cancelled, appt3));
        when(appointmentRepository.countActiveBySessionId(session.getId())).thenReturn(2L);
        when(appointmentRepository.findMaxNumberBySessionId(session.getId())).thenReturn(3);

        SessionResponse response = service.delaySession(session.getId(), new SessionDelayRequest(25));

        assertEquals(25, response.delayMinutes());
        assertEquals(LocalTime.of(9, 25), appt1.getEstimatedTime());
        assertEquals(LocalTime.of(9, 45), appt3.getEstimatedTime());
        assertEquals(LocalTime.of(9, 10), cancelled.getEstimatedTime());
        assertEquals(17, response.numbersLeft()); // 20 - highest issued (3), never reuses #2
    }

    @Test
    void cancelSessionCancelsActiveAppointmentsAndRecordsAuditLog() {
        ChannelSession session = sampleSession();
        UserAccount manager = new UserAccount();
        manager.setId(UUID.randomUUID());

        Appointment booked = new Appointment();
        booked.setAppointmentNumber(1);
        booked.setStatus(AppointmentStatus.BOOKED);

        when(sessionRepository.findWithDetailsById(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ChannelSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appointmentRepository.findBySessionIdOrderByAppointmentNumberAsc(session.getId()))
                .thenReturn(List.of(booked));
        when(appointmentRepository.countActiveBySessionId(session.getId())).thenReturn(0L);

        SessionResponse response = service.cancelSession(session.getId(), manager, "127.0.0.1");

        assertEquals(SessionStatus.CANCELLED, response.status());
        assertEquals(0, response.numbersLeft());
        assertEquals(AppointmentStatus.CANCELLED, booked.getStatus());
        assertNotNull(booked.getCancelledAt());
        verify(auditService).record(eq(manager), eq(AuditAction.SESSION_CANCELLED), eq("ChannelSession"), eq(session.getId()), eq("127.0.0.1"));
        verify(eventPublisher).publishEvent(any(SessionChangedEvent.class));
    }

    private Doctor sampleDoctor() {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setFullName("Dr. Nimal Silva");

        Specialisation spec = new Specialisation();
        spec.setId(UUID.randomUUID());
        spec.setName("Cardiology");

        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setUser(user);
        doctor.setSpecialisation(spec);
        doctor.setSlmcNumber("SLMC-12345");
        doctor.setQualifications("MBBS, MD");
        doctor.setConsultationFee(new BigDecimal("3500.00"));
        doctor.setActive(true);
        return doctor;
    }

    private Room sampleRoom() {
        Room room = new Room();
        room.setId(UUID.randomUUID());
        room.setName("Room 101");
        room.setLocation("1st Floor");
        room.setActive(true);
        return room;
    }

    private ChannelSession sampleSession() {
        ChannelSession session = new ChannelSession();
        session.setId(UUID.randomUUID());
        session.setDoctor(sampleDoctor());
        session.setRoom(sampleRoom());
        session.setSessionDate(LocalDate.now(ChannelSessionService.COLOMBO_ZONE).plusDays(1));
        session.setStartTime(LocalTime.of(9, 0));
        session.setEndTime(LocalTime.of(12, 0));
        session.setSessionType(SessionType.PHYSICAL);
        session.setMaxPatients(20);
        session.setMinutesPerPatient(10);
        session.setFee(new BigDecimal("3500.00"));
        session.setDelayMinutes(0);
        session.setStatus(SessionStatus.OPEN);
        return session;
    }
}