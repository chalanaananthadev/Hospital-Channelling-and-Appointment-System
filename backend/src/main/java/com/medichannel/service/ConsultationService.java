package com.medichannel.service;

import com.medichannel.dto.consultation.ConsultationResponse;
import com.medichannel.dto.consultation.ConsultationUpsertRequest;
import com.medichannel.entity.Appointment;
import com.medichannel.entity.ChannelSession;
import com.medichannel.entity.Consultation;
import com.medichannel.entity.Doctor;
import com.medichannel.entity.UserAccount;
import com.medichannel.enums.AppointmentStatus;
import com.medichannel.enums.AuditAction;
import com.medichannel.enums.Role;
import com.medichannel.event.QueueUpdatedEvent;
import com.medichannel.exception.ApiException;
import com.medichannel.repository.AppointmentRepository;
import com.medichannel.repository.ConsultationRepository;
import com.medichannel.security.MedicalPrivacyGuard;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ConsultationService {

    private final ConsultationRepository consultationRepository;
    private final AppointmentRepository appointmentRepository;
    private final MedicalPrivacyGuard medicalPrivacyGuard;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public ConsultationService(
            ConsultationRepository consultationRepository,
            AppointmentRepository appointmentRepository,
            MedicalPrivacyGuard medicalPrivacyGuard,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.consultationRepository = consultationRepository;
        this.appointmentRepository = appointmentRepository;
        this.medicalPrivacyGuard = medicalPrivacyGuard;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ConsultationResponse getByAppointmentId(UUID appointmentId, UserAccount actor, String ipAddress) {
        Appointment appointment = loadAppointment(appointmentId);
        enforceClinicalReadAccess(actor, appointment);

        Consultation consultation = consultationRepository.findByAppointmentId(appointmentId)
                .orElseGet(() -> openIfDoctorInConsultation(actor, appointment));

        auditService.record(
                actor,
                AuditAction.CLINICAL_RECORD_OPENED,
                "Consultation",
                consultation.getId(),
                ipAddress
        );

        return toResponse(consultation, appointment);
    }

    @Transactional
    public ConsultationResponse saveNotes(
            UUID appointmentId,
            ConsultationUpsertRequest request,
            UserAccount actor
    ) {
        Appointment appointment = loadAppointment(appointmentId);
        enforceTreatingDoctorWriteAccess(actor, appointment);

        Consultation consultation = consultationRepository.findByAppointmentId(appointmentId)
                .orElseGet(() -> openIfDoctorInConsultation(actor, appointment));

        if (consultation.isReadOnly()) {
            throw new ApiException(HttpStatus.CONFLICT, "Consultation has ended and is now read-only");
        }

        applyClinicalFields(consultation, request);
        Consultation saved = consultationRepository.save(consultation);
        return toResponse(saved, appointment);
    }

    @Transactional
    public ConsultationResponse endConsultation(
            UUID appointmentId,
            ConsultationUpsertRequest request,
            UserAccount actor
    ) {
        Appointment appointment = loadAppointment(appointmentId);
        enforceTreatingDoctorWriteAccess(actor, appointment);

        Consultation consultation = consultationRepository.findByAppointmentId(appointmentId)
                .orElseGet(() -> openIfDoctorInConsultation(actor, appointment));

        if (consultation.isReadOnly()) {
            throw new ApiException(HttpStatus.CONFLICT, "Consultation is already ended and read-only");
        }

        if (request != null) {
            applyClinicalFields(consultation, request);
        }

        if (consultation.getDiagnosis() == null || consultation.getDiagnosis().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Diagnosis summary is required before ending the consultation"
            );
        }

        consultation.setReadOnly(true);
        consultation.setEndedAt(Instant.now());
        Consultation savedConsultation = consultationRepository.save(consultation);

        if (appointment.getStatus() == AppointmentStatus.IN_CONSULTATION) {
            appointment.setStatus(AppointmentStatus.COMPLETED);
            appointmentRepository.save(appointment);

            ChannelSession session = appointment.getSession();
            eventPublisher.publishEvent(new QueueUpdatedEvent(
                    session.getId(),
                    QueueUpdatedEvent.ActionType.PATIENT_COMPLETED,
                    session.getStatus(),
                    session.getSessionType(),
                    session.getNowServing(),
                    appointment.getId(),
                    appointment.getStatus(),
                    appointment.getPatient().getUser().getId(),
                    null
            ));
        }

        return toResponse(savedConsultation, appointment);
    }

    // ==================== HELPERS ====================

    private Appointment loadAppointment(UUID appointmentId) {
        return appointmentRepository.findWithDetailsById(appointmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Appointment not found"));
    }

    private void enforceClinicalReadAccess(UserAccount actor, Appointment appointment) {
        UUID patientUserId = appointment.getPatient().getUser().getId();
        UUID doctorUserId = appointment.getSession().getDoctor().getUser().getId();
        medicalPrivacyGuard.assertCanReadClinical(actor, patientUserId, doctorUserId);
    }

    private void enforceTreatingDoctorWriteAccess(UserAccount actor, Appointment appointment) {
        enforceClinicalReadAccess(actor, appointment);
        if (actor.getRole() != Role.DOCTOR) {
            throw new AccessDeniedException("Only the treating doctor can author consultation notes");
        }
    }

    /**
     * BR-09: Consultation opens only when the doctor has started the appointment (IN_CONSULTATION).
     */
    private Consultation openIfDoctorInConsultation(UserAccount actor, Appointment appointment) {
        if (actor.getRole() == Role.DOCTOR && appointment.getStatus() == AppointmentStatus.IN_CONSULTATION) {
            Consultation consultation = new Consultation();
            consultation.setAppointment(appointment);
            consultation.setDoctor(appointment.getSession().getDoctor());
            consultation.setReadOnly(false);
            consultation.setStartedAt(Instant.now());
            return consultationRepository.save(consultation);
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "Consultation has not been started by the doctor yet");
    }

    private void applyClinicalFields(Consultation consultation, ConsultationUpsertRequest request) {
        consultation.setDiagnosis(trimToNull(request.diagnosis()));
        consultation.setAdvice(trimToNull(request.advice()));
        consultation.setPrescription(trimToNull(request.prescription()));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ConsultationResponse toResponse(Consultation consultation, Appointment appointment) {
        ChannelSession session = appointment.getSession();
        Doctor doctor = session.getDoctor();
        return new ConsultationResponse(
                consultation.getId(),
                appointment.getId(),
                appointment.getAppointmentNumber(),
                appointment.getBookingReference(),
                appointment.getStatus(),
                session.getSessionType(),
                session.getSessionDate(),
                session.getStartTime(),
                session.getRoom() == null ? null : session.getRoom().getName(),
                doctor.getId(),
                doctor.getUser().getFullName(),
                doctor.getSlmcNumber(),
                doctor.getQualifications(),
                doctor.getSpecialisation().getName(),
                appointment.getPatient().getId(),
                appointment.getPatient().getUser().getFullName(),
                appointment.getReasonForVisit(),
                consultation.getDiagnosis(),
                consultation.getAdvice(),
                consultation.getPrescription(),
                consultation.isReadOnly(),
                consultation.getStartedAt(),
                consultation.getEndedAt(),
                consultation.getUpdatedAt()
        );
    }
}