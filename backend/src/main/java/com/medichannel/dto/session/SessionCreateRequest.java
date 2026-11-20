package com.medichannel.dto.session;

import com.medichannel.enums.SessionStatus;
import com.medichannel.enums.SessionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record SessionCreateRequest(
        @NotNull(message = "Doctor is required")
        UUID doctorId,

        UUID roomId,

        @NotNull(message = "Session date is required")
        LocalDate sessionDate,

        @NotNull(message = "Start time is required")
        LocalTime startTime,

        @NotNull(message = "End time is required")
        LocalTime endTime,

        @NotNull(message = "Session type is required")
        SessionType sessionType,

        @NotNull(message = "Maximum patients is required")
        @Min(value = 1, message = "Maximum patients must be at least 1")
        @Max(value = 60, message = "Maximum patients must be at most 60")
        Integer maxPatients,

        @NotNull(message = "Minutes per patient is required")
        @Min(value = 5, message = "Minutes per patient must be at least 5")
        @Max(value = 30, message = "Minutes per patient must be at most 30")
        Integer minutesPerPatient,

        @NotNull(message = "Session fee is required")
        @DecimalMin(value = "0.01", message = "Session fee must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Session fee must be a valid LKR amount")
        BigDecimal fee,

        SessionStatus status
) {}