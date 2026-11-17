package com.hyouem.shortener;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class LinkController {

    private final LinkService service;
    private final AnalyticsService analyticsService;

    public LinkController(
            LinkService service,
            AnalyticsService analyticsService) {
        this.service = service;
        this.analyticsService = analyticsService;
    }

    @PostMapping("/api/links")
    public ResponseEntity<LinkResponse> create(
            @Valid @RequestBody CreateLinkRequest request) {

        String baseUrl = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .build()
                .toUriString();

        LinkResponse response = service.create(request, baseUrl);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/api/links/{code}")
    public LinkResponse get(@PathVariable String code) {
        String baseUrl = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .build()
                .toUriString();

        return service.get(code, baseUrl);
    }

    @DeleteMapping("/api/links/{code}")
    public ResponseEntity<Void> deactivate(
            @PathVariable String code,
            @RequestHeader(value = "X-Management-Key", required = false)
            String managementKey) {
        service.deactivate(code, managementKey);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String originalUrl = analyticsService.resolveAndTrack(code);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }
}