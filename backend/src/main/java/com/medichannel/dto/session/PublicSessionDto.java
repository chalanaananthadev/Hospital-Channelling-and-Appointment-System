package com.medichannel.dto.session;

import com.medichannel.enums.SessionStatus;
import com.medichannel.enums.SessionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record PublicSessionDto(
        UUID id,
        UUID doctorId,
        String doctorName,
        String doctorQualifications,
        UUID specialisationId,
        String specialisationName,
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
        int numbersLeft
) {}