package com.medichannel.dto.session;

import com.medichannel.enums.SessionStatus;
import jakarta.validation.constraints.NotNull;

public record SessionStatusUpdateRequest(
        @NotNull(message = "Session status is required")
        SessionStatus status
) {}