package com.hyouem.shortener;

public record AnalyticsResponse(
        String code,
        long totalClicks
) {
}