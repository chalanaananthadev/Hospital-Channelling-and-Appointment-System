package com.medichannel.repository;

import com.medichannel.entity.Consultation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsultationRepository extends JpaRepository<Consultation, UUID> {

    Optional<Consultation> findByAppointmentId(UUID appointmentId);

    List<Consultation> findByAppointmentIdIn(Collection<UUID> appointmentIds);

    @EntityGraph(attributePaths = {
            "appointment",
            "appointment.patient",
            "appointment.patient.user",
            "appointment.session",
            "appointment.session.room",
            "doctor",
            "doctor.user",
            "doctor.specialisation"
    })
    Optional<Consultation> findWithDetailsByAppointmentId(UUID appointmentId);
}