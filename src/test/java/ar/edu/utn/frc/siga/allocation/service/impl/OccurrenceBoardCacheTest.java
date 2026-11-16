package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceBoardEventDto;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceIssueType;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("OccurrenceBoardCache")
class OccurrenceBoardCacheTest {

    private static final LocalDate FROM = LocalDate.of(2027, 3, 1);

    private final OccurrenceBoardCache cache = new OccurrenceBoardCache();
    private final AtomicInteger loads = new AtomicInteger();
    private final TransactionTemplate transaction = new TransactionTemplate(new NoOpTransactionManager());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void shutDownExecutor() {
        executor.shutdownNow();
    }

    private static OccurrenceBoardCache.Key key(LocalDate from, Set<OccurrenceIssueType> issues) {
        return new OccurrenceBoardCache.Key(BuildingScope.unrestricted(), from, from.plusMonths(6), true, issues,
                null, null, null);
    }

    private static OccurrenceBoardCache.Key key(LocalDate from) {
        return key(from, null);
    }

    private List<OccurrenceBoardEventDto> load(OccurrenceBoardCache.Key key) {
        return cache.get(key, () -> {
            loads.incrementAndGet();
            return List.of();
        });
    }

    private boolean isCached(OccurrenceBoardCache.Key key) {
        try {
            cache.get(key, () -> {
                throw new NotCached();
            });
            return true;
        } catch (NotCached e) {
            return false;
        }
    }

    @Test
    @DisplayName("una segunda lectura con la misma clave no vuelve a calcular")
    void sameKeyIsLoadedOnce() {
        load(key(FROM));
        load(key(FROM));

        assertThat(loads).hasValue(1);
    }

    @Test
    @DisplayName("issues null, Set vacío y EnumSet vacío son la misma clave; el orden de issues no importa")
    void emptyIssuesShareKeyAndOrderDoesNotMatter() {
        load(key(FROM, null));
        load(key(FROM, Set.of()));
        load(key(FROM, EnumSet.noneOf(OccurrenceIssueType.class)));
        load(key(FROM.plusDays(1), EnumSet.of(OccurrenceIssueType.UNALLOCATED, OccurrenceIssueType.OVERLAP)));
        load(key(FROM.plusDays(1), Set.of(OccurrenceIssueType.OVERLAP, OccurrenceIssueType.UNALLOCATED)));

        assertThat(loads).hasValue(2);
    }

    @Test
    @DisplayName("fuera de una transacción, invalidate vacía la caché enseguida")
    void invalidateOutsideTransactionClearsImmediately() {
        load(key(FROM));

        cache.invalidate();
        load(key(FROM));

        assertThat(loads).hasValue(2);
    }

    @Test
    @DisplayName("dentro de una transacción, invalidate vacía la caché recién al confirmar")
    void invalidateInsideTransactionClearsAfterCommit() {
        load(key(FROM));

        transaction.executeWithoutResult(status -> {
            cache.invalidate();
            load(key(FROM));
            assertThat(loads).hasValue(1);
        });
        load(key(FROM));

        assertThat(loads).hasValue(2);
    }

    @Test
    @DisplayName("si la transacción hace rollback, invalidate no vacía la caché")
    void invalidateInsideRolledBackTransactionKeepsEntries() {
        load(key(FROM));

        transaction.executeWithoutResult(status -> {
            cache.invalidate();
            status.setRollbackOnly();
        });
        load(key(FROM));

        assertThat(loads).hasValue(1);
    }

    @Test
    @DisplayName("si se confirma una escritura mientras se calcula, el resultado se devuelve pero no queda guardado")
    void writeCommittedDuringLoadDiscardsResult() throws Exception {
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch written = new CountDownLatch(1);
        List<OccurrenceBoardEventDto> stale = List.of(new OccurrenceBoardEventDto(null, null, List.of()));
        Future<List<OccurrenceBoardEventDto>> reader = executor.submit(() -> cache.get(key(FROM), () -> {
            loads.incrementAndGet();
            loading.countDown();
            awaitLatch(written);
            return stale;
        }));

        assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();
        transaction.executeWithoutResult(status -> cache.invalidate());
        written.countDown();

        assertThat(reader.get(5, TimeUnit.SECONDS)).isEqualTo(stale);
        assertThat(isCached(key(FROM))).isFalse();
        load(key(FROM));
        assertThat(loads).hasValue(2);
    }

    @Test
    @DisplayName("después de una invalidación, la lectura siguiente vuelve a quedar guardada")
    void loadAfterInvalidationIsCached() {
        cache.invalidate();
        load(key(FROM));

        assertThat(isCached(key(FROM))).isTrue();
    }

    @Test
    @DisplayName("con MAX_ENTRIES + 1 claves distintas, la caché desaloja hasta quedar en MAX_ENTRIES")
    void evictsBeyondMaxEntries() {
        List<OccurrenceBoardCache.Key> keys = IntStream.rangeClosed(0, OccurrenceBoardCache.MAX_ENTRIES)
                .mapToObj(i -> key(FROM.plusDays(i)))
                .toList();
        keys.forEach(this::load);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(keys.stream().filter(this::isCached).count()).isEqualTo(OccurrenceBoardCache.MAX_ENTRIES));
        assertThat(OccurrenceBoardCache.MAX_ENTRIES).isEqualTo(5);
    }

    @Test
    @DisplayName("la entrada vence a los 30 s de escrita y la lectura siguiente vuelve a calcular")
    void entryExpiresAfterTtl() {
        AtomicLong nanos = new AtomicLong();
        OccurrenceBoardCache ticking = new OccurrenceBoardCache(nanos::get);
        Supplier<List<OccurrenceBoardEventDto>> loader = () -> {
            loads.incrementAndGet();
            return List.of();
        };

        ticking.get(key(FROM), loader);
        nanos.set(OccurrenceBoardCache.TTL.toNanos() - 1);
        ticking.get(key(FROM), loader);
        assertThat(loads).hasValue(1);

        nanos.set(OccurrenceBoardCache.TTL.toNanos());
        ticking.get(key(FROM), loader);
        assertThat(loads).hasValue(2);
        assertThat(OccurrenceBoardCache.TTL).isEqualTo(Duration.ofSeconds(30));
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static final class NotCached extends RuntimeException {
    }

    private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
