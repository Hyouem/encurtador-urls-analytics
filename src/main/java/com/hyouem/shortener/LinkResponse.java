package com.hyouem.shortener;

import java.time.Instant;

public record LinkResponse(
        String code,
        String originalUrl,
        String shortUrl,
        Instant createdAt,
        Instant expiresAt,
        boolean active,
        @com.fasterxml.jackson.annotation.JsonInclude(
                com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        String managementKey
) {
}