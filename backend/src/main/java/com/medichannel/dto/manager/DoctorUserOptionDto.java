package com.medichannel.dto.manager;

import java.util.UUID;

public record DoctorUserOptionDto(
        UUID userId,
        String fullName,
        String email
) {}