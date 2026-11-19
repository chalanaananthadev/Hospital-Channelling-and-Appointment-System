package com.medichannel.dto.manager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DoctorResponse(
        UUID id,
        UUID userId,
        String fullName,
        String email,
        UUID specialisationId,
        String specialisationName,
        String slmcNumber,
        String qualifications,
        BigDecimal consultationFee,
        String bio,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}