package com.medichannel.dto.doctor;

import com.medichannel.enums.SessionStatus;
import com.medichannel.enums.SessionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record DoctorSessionDto(
        UUID id,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        SessionType sessionType,
        String roomName,
        String roomLocation,
        int maxPatients,
        int minutesPerPatient,
        BigDecimal fee,
        int delayMinutes,
        SessionStatus status,
        Integer nowServing,
        int totalActiveBookings,
        int arrivedCount,
        int completedCount,
        int noShowCount,
        UUID currentAppointmentId
) {}