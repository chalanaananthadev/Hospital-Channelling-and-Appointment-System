package com.medichannel.dto.manager;

import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(
        @NotNull(message = "Active status is required")
        Boolean active
) {}