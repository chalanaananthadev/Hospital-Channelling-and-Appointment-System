package com.medichannel.repository;

import com.medichannel.entity.Appointment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    @Query("select coalesce(max(a.appointmentNumber), 0) from Appointment a where a.session.id = :sessionId")
    int findMaxNumberBySessionId(@Param("sessionId") UUID sessionId);

    @Query("""
            select count(a) from Appointment a
            where a.session.id = :sessionId
              and a.status <> com.medichannel.enums.AppointmentStatus.CANCELLED
            """)
    long countActiveBySessionId(@Param("sessionId") UUID sessionId);

    List<Appointment> findBySessionIdOrderByAppointmentNumberAsc(UUID sessionId);

    @EntityGraph(attributePaths = {"patient", "patient.user"})
    @Query("SELECT a FROM Appointment a WHERE a.session.id = :sessionId ORDER BY a.appointmentNumber ASC")
    List<Appointment> findQueueBySessionId(@Param("sessionId") UUID sessionId);

    @EntityGraph(attributePaths = {
            "patient",
            "patient.user",
            "session",
            "session.doctor",
            "session.doctor.user",
            "session.doctor.specialisation",
            "session.room"
    })
    Optional<Appointment> findWithDetailsById(UUID id);
}