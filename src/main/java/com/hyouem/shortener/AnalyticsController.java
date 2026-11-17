package com.hyouem.shortener;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/links")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/{code}/analytics")
    public AnalyticsResponse getAnalytics(@PathVariable String code) {
        return analyticsService.getAnalytics(code);
    }
}