package com.medichannel.dto.auth;

import com.medichannel.enums.Role;

import java.util.UUID;

public record MeResponse(
        UUID userId,
        Role role,
        String fullName,
        String email,
        boolean mustChangePassword
) {
}
