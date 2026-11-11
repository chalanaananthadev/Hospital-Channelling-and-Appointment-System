package com.medichannel.dto.catalog;

import java.math.BigDecimal;
import java.util.UUID;

public record PublicDoctorDto(
        UUID id,
        String fullName,
        String specialisation,
        UUID specialisationId,
        String qualifications,
        BigDecimal consultationFee,
        String bio
) {
}
