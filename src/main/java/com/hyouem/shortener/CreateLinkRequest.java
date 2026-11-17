package com.hyouem.shortener;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record CreateLinkRequest(
        @NotBlank String url,
        String alias,
        Instant expiresAt
) {
}