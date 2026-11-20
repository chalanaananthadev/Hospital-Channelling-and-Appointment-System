package com.medichannel.service;

import com.medichannel.dto.consultation.ConsultationResponse;
import com.medichannel.dto.consultation.ConsultationUpsertRequest;
import com.medichannel.entity.Appointment;
import com.medichannel.entity.ChannelSession;
import com.medichannel.entity.Consultation;
import com.medichannel.entity.Doctor;
import com.medichannel.entity.Patient;
import com.medichannel.entity.Specialisation;
import com.medichannel.entity.UserAccount;
import com.medichannel.enums.AppointmentStatus;
import com.medichannel.enums.AuditAction;
import com.medichannel.enums.Role;
import com.medichannel.enums.SessionStatus;
import com.medichannel.enums.SessionType;
import com.medichannel.event.QueueUpdatedEvent;
import com.medichannel.exception.ApiException;
import com.medichannel.repository.AppointmentRepository;
import com.medichannel.repository.ConsultationRepository;
import com.medichannel.security.MedicalPrivacyGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultationServiceTest {

    @Mock
    private ConsultationRepository consultationRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ConsultationService service;

    @BeforeEach
    void setUp() {
        service = new ConsultationService(
                consultationRepository,
                appointmentRepository,
                new MedicalPrivacyGuard(),
                auditService,
                eventPublisher
        );
    }

    @Test
    void treatingDoctorCanSaveAndEndConsultationWhichLocksReadOnly_BR09() {
        Appointment appointment = sampleAppointment(AppointmentStatus.IN_CONSULTATION);
        UserAccount doctorUser = appointment.getSession().getDoctor().getUser();
        Consultation consultation = sampleConsultation(appointment, false);

        when(appointmentRepository.findWithDetailsById(appointment.getId())).thenReturn(Optional.of(appointment));
        when(consultationRepository.findByAppointmentId(appointment.getId())).thenReturn(Optional.of(consultation));
        when(consultationRepository.save(any(Consultation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        ConsultationUpsertRequest request = new ConsultationUpsertRequest(
                "Mild seasonal allergic rhinitis",
                "Avoid dust exposure and stay hydrated",
                "Cetirizine 10mg once daily at night for 5 days"
        );

        ConsultationResponse saved = service.saveNotes(appointment.getId(), request, doctorUser);
        assertEquals("Mild seasonal allergic rhinitis", saved.diagnosis());

        ConsultationResponse ended = service.endConsultation(appointment.getId(), request, doctorUser);
        assertTrue(ended.readOnly());
        assertNotNull(ended.endedAt());
        assertEquals(AppointmentStatus.COMPLETED, appointment.getStatus());
        verify(eventPublisher).publishEvent(any(QueueUpdatedEvent.class));

        // Once read-only, further edits must be rejected with 409 Conflict
        ApiException lockedEx = assertThrows(ApiException.class,
                () -> service.saveNotes(appointment.getId(), request, doctorUser));
        assertEquals(HttpStatus.CONFLICT, lockedEx.getStatus());
    }

    @Test
    void endConsultationRequiresDiagnosisSummary() {
        Appointment appointment = sampleAppointment(AppointmentStatus.IN_CONSULTATION);
        UserAccount doctorUser = appointment.getSession().getDoctor().getUser();
        Consultation consultation = sampleConsultation(appointment, false);

        when(appointmentRepository.findWithDetailsById(appointment.getId())).thenReturn(Optional.of(appointment));
        when(consultationRepository.findByAppointmentId(appointment.getId())).thenReturn(Optional.of(consultation));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.endConsultation(appointment.getId(), new ConsultationUpsertRequest("   ", "Rest", null), doctorUser));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void owningPatientCanReadSummaryAndRecordsAuditLog() {
        Appointment appointment = sampleAppointment(AppointmentStatus.COMPLETED);
        UserAccount patientUser = appointment.getPatient().getUser();
        Consultation consultation = sampleConsultation(appointment, true);
        consultation.setDiagnosis("Viral fever");

        when(appointmentRepository.findWithDetailsById(appointment.getId())).thenReturn(Optional.of(appointment));
        when(consultationRepository.findByAppointmentId(appointment.getId())).thenReturn(Optional.of(consultation));

        ConsultationResponse response = service.getByAppointmentId(appointment.getId(), patientUser, "192.168.1.10");

        assertEquals("Viral fever", response.diagnosis());
        verify(auditService).record(
                eq(patientUser),
                eq(AuditAction.CLINICAL_RECORD_OPENED),
                eq("Consultation"),
                eq(consultation.getId()),
                eq("192.168.1.10")
        );
    }

    @Test
    void blocksAdminManagerOtherDoctorAndOtherPatientFromClinicalData() {
        Appointment appointment = sampleAppointment(AppointmentStatus.COMPLETED);
        when(appointmentRepository.findWithDetailsById(appointment.getId())).thenReturn(Optional.of(appointment));

        UserAccount admin = userWithRole(Role.ADMIN);
        UserAccount manager = userWithRole(Role.MANAGER);
        UserAccount otherDoctor = userWithRole(Role.DOCTOR);
        UserAccount otherPatient = userWithRole(Role.CUSTOMER);

        assertThrows(AccessDeniedException.class,
                () -> service.getByAppointmentId(appointment.getId(), admin, "127.0.0.1"));
        assertThrows(AccessDeniedException.class,
                () -> service.getByAppointmentId(appointment.getId(), manager, "127.0.0.1"));
        assertThrows(AccessDeniedException.class,
                () -> service.getByAppointmentId(appointment.getId(), otherDoctor, "127.0.0.1"));
        assertThrows(AccessDeniedException.class,
                () -> service.getByAppointmentId(appointment.getId(), otherPatient, "127.0.0.1"));
    }

    @Test
    void patientCannotWriteConsultationNotes() {
        Appointment appointment = sampleAppointment(AppointmentStatus.IN_CONSULTATION);
        UserAccount patientUser = appointment.getPatient().getUser();

        when(appointmentRepository.findWithDetailsById(appointment.getId())).thenReturn(Optional.of(appointment));

        assertThrows(AccessDeniedException.class,
                () -> service.saveNotes(appointment.getId(), new ConsultationUpsertRequest("Dx", null, null), patientUser));
    }

    @Test
    void cannotOpenConsultationBeforeDoctorStartsAppointment_BR09() {
        Appointment booked = sampleAppointment(AppointmentStatus.BOOKED);
        UserAccount patientUser = booked.getPatient().getUser();

        when(appointmentRepository.findWithDetailsById(booked.getId())).thenReturn(Optional.of(booked));
        when(consultationRepository.findByAppointmentId(booked.getId())).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> service.getByAppointmentId(booked.getId(), patientUser, "127.0.0.1"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    private UserAccount userWithRole(Role role) {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setRole(role);
        user.setFullName(role.name() + " User");
        return user;
    }

    private Appointment sampleAppointment(AppointmentStatus status) {
        UserAccount doctorUser = userWithRole(Role.DOCTOR);
        doctorUser.setFullName("Dr. Nimal Silva");

        Specialisation spec = new Specialisation();
        spec.setId(UUID.randomUUID());
        spec.setName("Cardiology");

        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setUser(doctorUser);
        doctor.setSpecialisation(spec);
        doctor.setSlmcNumber("SLMC-12345");
        doctor.setQualifications("MBBS, MD");

        ChannelSession session = new ChannelSession();
        session.setId(UUID.randomUUID());
        session.setDoctor(doctor);
        session.setSessionDate(LocalDate.now(ChannelSessionService.COLOMBO_ZONE));
        session.setStartTime(LocalTime.of(9, 0));
        session.setSessionType(SessionType.ONLINE);
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setNowServing(1);

        UserAccount patientUser = userWithRole(Role.CUSTOMER);
        patientUser.setFullName("Kamal Perera");

        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setUser(patientUser);

        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setSession(session);
        appointment.setPatient(patient);
        appointment.setAppointmentNumber(1);
        appointment.setAppointmentType(SessionType.ONLINE);
        appointment.setStatus(status);
        appointment.setBookingReference("MC-2026-001");
        appointment.setReasonForVisit("Persistent cough");
        return appointment;
    }

    private Consultation sampleConsultation(Appointment appointment, boolean readOnly) {
        Consultation consultation = new Consultation();
        consultation.setId(UUID.randomUUID());
        consultation.setAppointment(appointment);
        consultation.setDoctor(appointment.getSession().getDoctor());
        consultation.setReadOnly(readOnly);
        consultation.setStartedAt(Instant.now());
        if (readOnly) {
            consultation.setEndedAt(Instant.now());
        }
        return consultation;
    }
}