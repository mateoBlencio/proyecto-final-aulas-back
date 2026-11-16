package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceBoardEventDto;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceIssueType;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Component
class OccurrenceBoardCache {

    static final Duration TTL = Duration.ofSeconds(30);
    static final int MAX_ENTRIES = 5;

    record Key(BuildingScope scope, LocalDate from, LocalDate to, boolean onlyFuture,
               Set<OccurrenceIssueType> issues, String q, DayOfWeek dayOfWeek, String specialty) {

        Key {
            issues = issues == null ? Set.of() : Set.copyOf(issues);
        }
    }

    private final Cache<Key, List<OccurrenceBoardEventDto>> cache;
    private final AtomicLong generation = new AtomicLong();

    public OccurrenceBoardCache() {
        this(Ticker.systemTicker());
    }

    OccurrenceBoardCache(Ticker ticker) {
        cache = Caffeine.newBuilder()
                .ticker(ticker)
                .expireAfterWrite(TTL)
                .maximumSize(MAX_ENTRIES)
                .build();
    }

    List<OccurrenceBoardEventDto> get(Key key, Supplier<List<OccurrenceBoardEventDto>> loader) {
        List<OccurrenceBoardEventDto> cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        long seen = generation.get();
        List<OccurrenceBoardEventDto> loaded = List.copyOf(loader.get());
        cache.put(key, loaded);
        if (generation.get() != seen) {
            cache.invalidate(key);
        }
        return loaded;
    }

    void invalidate() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    clear();
                }
            });
        } else {
            clear();
        }
    }

    private void clear() {
        generation.incrementAndGet();
        cache.invalidateAll();
    }
}
