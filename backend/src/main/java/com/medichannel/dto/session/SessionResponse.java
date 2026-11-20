package com.medichannel.dto.session;

import com.medichannel.enums.SessionStatus;
import com.medichannel.enums.SessionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        UUID doctorId,
        String doctorName,
        String doctorSlmcNumber,
        UUID specialisationId,
        String specialisationName,
        UUID roomId,
        String roomName,
        String roomLocation,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        SessionType sessionType,
        int maxPatients,
        int minutesPerPatient,
        BigDecimal fee,
        int delayMinutes,
        SessionStatus status,
        Integer nowServing,
        int bookedCount,
        int numbersLeft,
        Instant createdAt,
        Instant updatedAt
) {}