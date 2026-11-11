package com.medichannel.dto.auth;

import com.medichannel.enums.Role;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        UUID userId,
        Role role,
        String fullName,
        boolean mustChangePassword
) {
}
