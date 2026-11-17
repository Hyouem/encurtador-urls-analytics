package com.hyouem.shortener;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AnalyticsService {

    private final LinkService linkService;
    private final LinkAccessEventRepository events;
    private final LinkCache cache;

    public AnalyticsService(
            LinkService linkService,
            LinkAccessEventRepository events,
            LinkCache cache) {
        this.linkService = linkService;
        this.events = events;
        this.cache = cache;
    }

    @Transactional
    public String resolveAndTrack(String code) {
        ShortLink link = linkService.resolve(code);
        events.saveAndFlush(new LinkAccessEvent(link));

        String cachedUrl = cache.get(code);
        if (cachedUrl != null && cachedUrl.equals(link.getOriginalUrl())) {
            return cachedUrl;
        }

        String originalUrl = link.getOriginalUrl();
        java.time.Instant expiresAt = link.getExpiresAt();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cache.put(code, originalUrl, expiresAt);
            }
        });
        return originalUrl;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics(String code) {
        ShortLink link = linkService.findByCode(code);

        return new AnalyticsResponse(
                link.getCode(),
                events.countByLink_Id(link.getId())
        );
    }
}