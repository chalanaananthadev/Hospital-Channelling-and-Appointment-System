package com.medichannel.dto.session;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SessionDelayRequest(
        @NotNull(message = "Delay minutes is required")
        @Min(value = 0, message = "Delay minutes cannot be negative")
        @Max(value = 360, message = "Delay minutes cannot exceed 360 minutes")
        Integer delayMinutes
) {}