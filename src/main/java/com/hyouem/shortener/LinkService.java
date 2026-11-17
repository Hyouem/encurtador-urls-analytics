package com.hyouem.shortener;

import java.net.URI;
import java.time.Instant;
import java.util.regex.Pattern;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LinkService {

    private static final Pattern ALIAS =
            Pattern.compile("[a-zA-Z0-9_-]{3,32}");

    private final ShortLinkRepository repository;
    private final CodeGenerator generator;
    private final LinkCache cache;

    public LinkService(
            ShortLinkRepository repository,
            CodeGenerator generator,
            LinkCache cache) {
        this.repository = repository;
        this.generator = generator;
        this.cache = cache;
    }

    public LinkResponse create(CreateLinkRequest request, String baseUrl) {
        String url = validateUrl(request.url());

        if (request.expiresAt() != null &&
                !request.expiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Expiration must be in the future");
        }

        String alias = request.alias();

        if (alias != null && !ALIAS.matcher(alias).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid alias");
        }

        if (alias != null) {
            return save(alias, url, request.expiresAt(), baseUrl);
        }

        for (int attempt = 0; attempt < 5; attempt++) {
            String code = generator.generate();

            try {
                return save(code, url, request.expiresAt(), baseUrl);
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode() != HttpStatus.CONFLICT) {
                    throw exception;
                }
            }
        }

        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Unable to generate unique code");
    }

    private LinkResponse save(
            String code, String url, Instant expiresAt, String baseUrl) {

        if (repository.existsByCode(code)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Code already exists");
        }

        try {
            String managementKey = ManagementKeys.generate();
            ShortLink entity = new ShortLink(code, url, expiresAt);
            entity.setManagementKeyHash(ManagementKeys.hash(managementKey));

            ShortLink link = repository.saveAndFlush(entity);
            return new LinkResponse(
                    link.getCode(),
                    link.getOriginalUrl(),
                    baseUrl + "/" + link.getCode(),
                    link.getCreatedAt(),
                    link.getExpiresAt(),
                    link.isActive(),
                    managementKey);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Code already exists", exception);
        }
    }

    public ShortLink findByCode(String code) {
        return repository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Link not found"));
    }

    public ShortLink resolve(String code) {
        ShortLink link = repository.findByCodeForUpdate(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Link not found"));

        if (!link.isActive() || link.isExpired()) {
            throw new ResponseStatusException(
                    HttpStatus.GONE, "Link is inactive or expired");
        }

        return link;
    }

    @org.springframework.transaction.annotation.Transactional
    public void deactivate(String code, String managementKey) {
        ShortLink link = repository.findByCodeForUpdate(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Link not found"));

        if (!ManagementKeys.matches(
                managementKey, link.getManagementKeyHash())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Invalid management key");
        }

        link.deactivate();
        repository.saveAndFlush(link);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cache.invalidate(code);
            }
        });
    }

    public LinkResponse get(String code, String baseUrl) {
        ShortLink link = repository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Link not found"));

        return response(link, baseUrl);
    }

    private LinkResponse response(ShortLink link, String baseUrl) {
        return new LinkResponse(
                link.getCode(),
                link.getOriginalUrl(),
                baseUrl + "/" + link.getCode(),
                link.getCreatedAt(),
                link.getExpiresAt(),
                link.isActive() && !link.isExpired(),
                null);
    }

    private String validateUrl(String value) {
        if (value == null || value.length() > 2048) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid URL");
        }

        try {
            URI uri = URI.create(value);

            if (!uri.isAbsolute()
                    || uri.getHost() == null
                    || uri.getRawUserInfo() != null
                    || uri.getHost().isBlank()
                    || !("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException("Unsupported URL");
            }

            return uri.toString();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid URL");
        }
    }
}