package com.medichannel.repository;

import com.medichannel.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    @Query("select coalesce(max(a.appointmentNumber), 0) from Appointment a where a.session.id = :sessionId")
    int findMaxNumberBySessionId(@Param("sessionId") UUID sessionId);
}
