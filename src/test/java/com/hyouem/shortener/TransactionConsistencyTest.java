package com.hyouem.shortener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

class TransactionConsistencyTest {
    private final ShortLinkRepository repository = mock(ShortLinkRepository.class);
    private final LinkCache cache = mock(LinkCache.class);
    private final LinkService links = new LinkService(repository, mock(CodeGenerator.class), cache);

    @BeforeEach
    void startTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void stopTransactionSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void authorizedDeactivationInvalidatesOnlyAfterCommit() {
        String key = ManagementKeys.generate();
        ShortLink link = new ShortLink("abc123", "https://example.com", null);
        link.setManagementKeyHash(ManagementKeys.hash(key));
        when(repository.findByCodeForUpdate("abc123")).thenReturn(Optional.of(link));

        links.deactivate("abc123", key);
        assertFalse(link.isActive());
        verify(repository).saveAndFlush(link);
        verify(cache, never()).invalidate(anyString());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(cache).invalidate("abc123");
    }

    @Test
    void rollbackDoesNotInvalidateCache() {
        String key = ManagementKeys.generate();
        ShortLink link = new ShortLink("abc123", "https://example.com", null);
        link.setManagementKeyHash(ManagementKeys.hash(key));
        when(repository.findByCodeForUpdate("abc123")).thenReturn(Optional.of(link));

        links.deactivate("abc123", key);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(cache, never()).invalidate(anyString());
    }

    @Test
    void invalidManagementKeyIsForbidden() {
        ShortLink link = new ShortLink("abc123", "https://example.com", null);
        link.setManagementKeyHash(ManagementKeys.hash(ManagementKeys.generate()));
        when(repository.findByCodeForUpdate("abc123")).thenReturn(Optional.of(link));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> links.deactivate("abc123", "invalid"));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertTrue(link.isActive());
        verify(repository, never()).saveAndFlush(any());
        verify(cache, never()).invalidate(anyString());
    }

    @Test
    void redirectCachesOnlyAfterCommit() {
        LinkService linkService = mock(LinkService.class);
        LinkAccessEventRepository events = mock(LinkAccessEventRepository.class);
        AnalyticsService analytics = new AnalyticsService(linkService, events, cache);
        ShortLink link = new ShortLink("abc123", "https://example.com", null);
        when(linkService.resolve("abc123")).thenReturn(link);
        when(cache.get("abc123")).thenReturn(null);

        assertEquals("https://example.com", analytics.resolveAndTrack("abc123"));
        verify(events).saveAndFlush(any(LinkAccessEvent.class));
        verify(cache, never()).put(anyString(), anyString(), any());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(cache).put(eq("abc123"), eq("https://example.com"), isNull());
    }

    @Test
    void rollbackDoesNotPopulateCache() {
        LinkService linkService = mock(LinkService.class);
        LinkAccessEventRepository events = mock(LinkAccessEventRepository.class);
        AnalyticsService analytics = new AnalyticsService(linkService, events, cache);
        when(linkService.resolve("abc123"))
                .thenReturn(new ShortLink("abc123", "https://example.com", null));
        analytics.resolveAndTrack("abc123");
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(cache, never()).put(anyString(), anyString(), any());
    }
}