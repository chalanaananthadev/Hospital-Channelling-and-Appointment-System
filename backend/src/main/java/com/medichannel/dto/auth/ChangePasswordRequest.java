package com.medichannel.dto.auth;

import com.medichannel.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 72) String currentPassword,
        @NotBlank @StrongPassword @Size(max = 72) String newPassword
) {
}
