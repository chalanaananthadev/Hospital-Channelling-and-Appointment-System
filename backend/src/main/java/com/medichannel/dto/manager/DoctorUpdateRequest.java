package com.medichannel.dto.manager;

import com.medichannel.validation.SlmcNumber;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record DoctorUpdateRequest(
        @NotBlank(message = "Doctor full name is required")
        @Size(max = 120, message = "Full name must be at most 120 characters")
        String fullName,

        @NotNull(message = "Specialisation is required")
        UUID specialisationId,

        @NotBlank(message = "SLMC registration number is required")
        @Size(max = 30, message = "SLMC number must be at most 30 characters")
        @SlmcNumber
        String slmcNumber,

        @NotBlank(message = "Qualifications are required")
        @Size(max = 1000, message = "Qualifications must be at most 1000 characters")
        String qualifications,

        @NotNull(message = "Consultation fee is required")
        @DecimalMin(value = "0.01", message = "Consultation fee must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Consultation fee must be a valid LKR amount")
        BigDecimal consultationFee,

        @Size(max = 2000, message = "Bio must be at most 2000 characters")
        String bio
) {}