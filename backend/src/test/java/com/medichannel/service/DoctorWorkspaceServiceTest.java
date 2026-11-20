package com.medichannel.service;

import com.medichannel.dto.doctor.DoctorSessionQueueDto;
import com.medichannel.entity.Appointment;
import com.medichannel.entity.ChannelSession;
import com.medichannel.entity.Consultation;
import com.medichannel.entity.Doctor;
import com.medichannel.entity.Patient;
import com.medichannel.entity.Room;
import com.medichannel.entity.Specialisation;
import com.medichannel.entity.UserAccount;
import com.medichannel.enums.AppointmentStatus;
import com.medichannel.enums.PaymentStatus;
import com.medichannel.enums.SessionStatus;
import com.medichannel.enums.SessionType;
import com.medichannel.event.QueueUpdatedEvent;
import com.medichannel.exception.ApiException;
import com.medichannel.repository.AppointmentRepository;
import com.medichannel.repository.ChannelSessionRepository;
import com.medichannel.repository.ConsultationRepository;
import com.medichannel.repository.DoctorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorWorkspaceServiceTest {

    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private ChannelSessionRepository sessionRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private ConsultationRepository consultationRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private DoctorWorkspaceService service;

    @BeforeEach
    void setUp() {
        service = new DoctorWorkspaceService(
                doctorRepository,
                sessionRepository,
                appointmentRepository,
                consultationRepository,
                eventPublisher
        );
    }

    @Test
    void blocksAnotherDoctorFromAccessingSessionWith403() {
        ChannelSession session = sampleSession(SessionType.PHYSICAL, SessionStatus.OPEN);
        UUID otherDoctorUserId = UUID.randomUUID();

        when(sessionRepository.findWithDetailsById(session.getId())).thenReturn(Optional.of(session));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.getSessionQueue(otherDoctorUserId, session.getId()));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void physicalSessionCallNextOnlyCallsArrivedPatientAndNotifiesThreeAway_BR08() {
        ChannelSession session = sampleSession(SessionType.PHYSICAL, SessionStatus.IN_PROGRESS);
        UUID doctorUserId = session.getDoctor().getUser().getId();

        Appointment bookedNotArrived = sampleAppointment(session, 1, AppointmentStatus.BOOKED);
        Appointment arrivedFirst = sampleAppointment(session, 2, AppointmentStatus.ARRIVED);
        Appointment threeAway = sampleAppointment(session, 5, AppointmentStatus.BOOKED);

        when(sessionRepository.findWithDetailsById(session.getId())).thenReturn(Optional.of(session));
        when(appointmentRepository.findQueueBySessionId(session.getId()))
                .thenReturn(List.of(bookedNotArrived, arrivedFirst, threeAway));
        when(sessionRepository.save(any(ChannelSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(consultationRepository.findByAppointmentId(arrivedFirst.getId())).thenReturn(Optional.empty());
        when(consultationRepository.save(any(Consultation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(consultationRepository.findByAppointmentIdIn(anyCollection())).thenReturn(List.of());

        DoctorSessionQueueDto result = service.callNextPatient(doctorUserId, session.getId());

        assertEquals(AppointmentStatus.IN_CONSULTATION, arrivedFirst.getStatus());
        assertEquals(2, result.session().nowServing());

        ArgumentCaptor<QueueUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(QueueUpdatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(arrivedFirst.getPatient().getUser().getId(), eventCaptor.getValue().calledPatientUserId());
        assertEquals(threeAway.getPatient().getUser().getId(), eventCaptor.getValue().threeAwayPatientUserId());
    }

    @Test
    void onlineSessionCallNextTransitionsDirectlyFromBookedToInConsultation_BR08() {
        ChannelSession session = sampleSession(SessionType.ONLINE, SessionStatus.IN_PROGRESS);
        UUID doctorUserId = session.getDoctor().getUser().getId();

        Appointment onlineBooked = sampleAppointment(session, 1, AppointmentStatus.BOOKED);

        when(sessionRepository.findWithDetailsById(session.getId())).thenReturn(Optional.of(session));
        when(appointmentRepository.findQueueBySessionId(session.getId())).thenReturn(List.of(onlineBooked));
        when(sessionRepository.save(any(ChannelSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(consultationRepository.findByAppointmentId(onlineBooked.getId())).thenReturn(Optional.empty());
        when(consultationRepository.save(any(Consultation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(consultationRepository.findByAppointmentIdIn(anyCollection())).thenReturn(List.of());

        DoctorSessionQueueDto result = service.callNextPatient(doctorUserId, session.getId());

        assertEquals(AppointmentStatus.IN_CONSULTATION, onlineBooked.getStatus());
        assertEquals(1, result.session().nowServing());
        verify(consultationRepository).save(any(Consultation.class));
    }

    @Test
    void markCompletedLocksOpenConsultationReadOnly_BR09() {
        ChannelSession session = sampleSession(SessionType.ONLINE, SessionStatus.IN_PROGRESS);
        UUID doctorUserId = session.getDoctor().getUser().getId();
        Appointment inConsultation = sampleAppointment(session, 1, AppointmentStatus.IN_CONSULTATION);

        Consultation openConsultation = new Consultation();
        openConsultation.setId(UUID.randomUUID());
        openConsultation.setAppointment(inConsultation);
        openConsultation.setDoctor(session.getDoctor());
        openConsultation.setReadOnly(false);

        when(sessionRepository.findWithDetailsById(session.getId())).thenReturn(Optional.of(session));
        when(appointmentRepository.findWithDetailsById(inConsultation.getId())).thenReturn(Optional.of(inConsultation));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(consultationRepository.findByAppointmentId(inConsultation.getId()))
                .thenReturn(Optional.of(openConsultation));
        when(consultationRepository.save(any(Consultation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appointmentRepository.findQueueBySessionId(session.getId())).thenReturn(List.of(inConsultation));
        when(consultationRepository.findByAppointmentIdIn(anyCollection())).thenReturn(List.of(openConsultation));

        service.markCompleted(doctorUserId, session.getId(), inConsultation.getId());

        assertEquals(AppointmentStatus.COMPLETED, inConsultation.getStatus());
        assertTrue(openConsultation.isReadOnly());
        assertNotNull(openConsultation.getEndedAt());
    }

    @Test
    void endSessionRejectsWhenPatientStillInConsultation() {
        ChannelSession session = sampleSession(SessionType.PHYSICAL, SessionStatus.IN_PROGRESS);
        UUID doctorUserId = session.getDoctor().getUser().getId();
        Appointment active = sampleAppointment(session, 1, AppointmentStatus.IN_CONSULTATION);

        when(sessionRepository.findWithDetailsById(session.getId())).thenReturn(Optional.of(session));
        when(appointmentRepository.findQueueBySessionId(session.getId())).thenReturn(List.of(active));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.endSession(doctorUserId, session.getId()));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void markNoShowUpdatesStatusFromBooked() {
        ChannelSession session = sampleSession(SessionType.PHYSICAL, SessionStatus.IN_PROGRESS);
        UUID doctorUserId = session.getDoctor().getUser().getId();
        Appointment booked = sampleAppointment(session, 2, AppointmentStatus.BOOKED);

        when(sessionRepository.findWithDetailsById(session.getId())).thenReturn(Optional.of(session));
        when(appointmentRepository.findWithDetailsById(booked.getId())).thenReturn(Optional.of(booked));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(consultationRepository.findByAppointmentId(booked.getId())).thenReturn(Optional.empty());
        when(appointmentRepository.findQueueBySessionId(session.getId())).thenReturn(List.of(booked));
        when(consultationRepository.findByAppointmentIdIn(anyCollection())).thenReturn(List.of());

        service.markNoShow(doctorUserId, session.getId(), booked.getId());

        assertEquals(AppointmentStatus.NO_SHOW, booked.getStatus());
    }

    private ChannelSession sampleSession(SessionType type, SessionStatus status) {
        UserAccount doctorUser = new UserAccount();
        doctorUser.setId(UUID.randomUUID());
        doctorUser.setFullName("Dr. Nimal Silva");

        Specialisation spec = new Specialisation();
        spec.setId(UUID.randomUUID());
        spec.setName("Cardiology");

        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setUser(doctorUser);
        doctor.setSpecialisation(spec);
        doctor.setActive(true);

        ChannelSession session = new ChannelSession();
        session.setId(UUID.randomUUID());
        session.setDoctor(doctor);
        if (type == SessionType.PHYSICAL) {
            Room room = new Room();
            room.setId(UUID.randomUUID());
            room.setName("Room 101");
            session.setRoom(room);
        }
        session.setSessionDate(LocalDate.now(ChannelSessionService.COLOMBO_ZONE));
        session.setStartTime(LocalTime.of(9, 0));
        session.setEndTime(LocalTime.of(12, 0));
        session.setSessionType(type);
        session.setMaxPatients(20);
        session.setMinutesPerPatient(10);
        session.setFee(new BigDecimal("3500.00"));
        session.setStatus(status);
        return session;
    }

    private Appointment sampleAppointment(ChannelSession session, int number, AppointmentStatus status) {
        UserAccount patientUser = new UserAccount();
        patientUser.setId(UUID.randomUUID());
        patientUser.setFullName("Patient " + number);

        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setUser(patientUser);

        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setSession(session);
        appointment.setPatient(patient);
        appointment.setAppointmentNumber(number);
        appointment.setAppointmentType(session.getSessionType());
        appointment.setStatus(status);
        appointment.setPaymentStatus(PaymentStatus.PAID);
        appointment.setEstimatedTime(LocalTime.of(9, 0).plusMinutes((long) (number - 1) * 10));
        appointment.setBookingReference("MC-2026-" + number);
        appointment.setReasonForVisit("Chest discomfort follow-up");
        return appointment;
    }
}