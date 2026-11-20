package com.medichannel.dto.consultation;

import jakarta.validation.constraints.Size;

public record ConsultationUpsertRequest(
        @Size(max = 4000, message = "Diagnosis summary must be at most 4000 characters")
        String diagnosis,

        @Size(max = 4000, message = "Medical advice must be at most 4000 characters")
        String advice,

        @Size(max = 4000, message = "Prescription must be at most 4000 characters")
        String prescription
) {}