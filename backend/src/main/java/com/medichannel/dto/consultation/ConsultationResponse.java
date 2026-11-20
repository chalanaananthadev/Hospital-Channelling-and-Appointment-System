package com.medichannel.dto.consultation;

import com.medichannel.enums.AppointmentStatus;
import com.medichannel.enums.SessionType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record ConsultationResponse(
        UUID id,
        UUID appointmentId,
        int appointmentNumber,
        String bookingReference,
        AppointmentStatus appointmentStatus,
        SessionType sessionType,
        LocalDate sessionDate,
        LocalTime sessionStartTime,
        String roomName,
        UUID doctorId,
        String doctorName,
        String doctorSlmcNumber,
        String doctorQualifications,
        String specialisationName,
        UUID patientId,
        String patientName,
        String reasonForVisit,
        String diagnosis,
        String advice,
        String prescription,
        boolean readOnly,
        Instant startedAt,
        Instant endedAt,
        Instant updatedAt
) {}