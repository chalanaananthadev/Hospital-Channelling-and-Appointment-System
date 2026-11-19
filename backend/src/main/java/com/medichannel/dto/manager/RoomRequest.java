package com.medichannel.dto.manager;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoomRequest(
        @NotBlank(message = "Room name is required")
        @Size(max = 40, message = "Room name must be at most 40 characters")
        String name,

        @Size(max = 120, message = "Location must be at most 120 characters")
        String location
) {}