package com.medichannel.dto.manager;

import java.time.Instant;
import java.util.UUID;

public record RoomResponse(
        UUID id,
        String name,
        String location,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}