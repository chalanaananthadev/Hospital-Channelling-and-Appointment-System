package com.medichannel.dto.catalog;

import java.time.Instant;
import java.util.UUID;

public record NoticeDto(UUID id, String title, String body, Instant publishedAt) {
}
