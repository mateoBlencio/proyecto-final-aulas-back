package ar.edu.utn.frc.siga.preview.service.impl;

import ar.edu.utn.frc.siga.preview.config.PreviewSettings;
import ar.edu.utn.frc.siga.preview.service.ReallocationSuggestion;
import ar.edu.utn.frc.siga.settings.api.SettingChangedEvent;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerAllocation;
import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CaffeinePreviewStore")
class CaffeinePreviewStoreTest {

    private CaffeinePreviewStore store;

    @BeforeEach
    void setUp() {
        PreviewSettings previewSettings = mock(PreviewSettings.class);
        when(previewSettings.getTtlMinutes()).thenReturn(30L);
        store = new CaffeinePreviewStore(previewSettings);
        store.init();
    }

    @Test
    @DisplayName("save luego get devuelve la preview guardada; tras remove, get queda vacío")
    void saveGetRemove() {
        OptimizationResult preview = new OptimizationResult("prev_test", List.of(new OptimizerAllocation("1", 5L)));

        store.save(preview);
        assertThat(store.get("prev_test")).isPresent().contains(preview);

        store.remove("prev_test");
        assertThat(store.get("prev_test")).isEmpty();
    }

    @Test
    @DisplayName("takeSuggestion devuelve la sugerencia la primera vez y Optional.empty() la segunda")
    void takeSuggestionEsDeUnSoloUso() {
        ReallocationSuggestion suggestion = new ReallocationSuggestion("sug_test", 1L, List.of(10L), 5L);
        store.saveSuggestion(suggestion);

        assertThat(store.takeSuggestion("sug_test")).contains(suggestion);
        assertThat(store.takeSuggestion("sug_test")).isEmpty();
    }

    @Test
    @DisplayName("dos hilos concurrentes tomando la misma sugerencia: exactamente uno la recibe")
    void takeSuggestionConcurrenteEsAtomico() throws Exception {
        // Con get+remove separados (no atómico) los dos hilos podrían leer la sugerencia antes de que
        // cualquiera la invalide, y este assert daría 2 en vez de 1. asMap().remove() lo evita.
        ReallocationSuggestion suggestion = new ReallocationSuggestion("sug_race", 1L, List.of(10L), 5L);
        store.saveSuggestion(suggestion);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Optional<ReallocationSuggestion>>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return store.takeSuggestion("sug_race");
                }));
            }
            ready.await();
            start.countDown();

            AtomicInteger presentCount = new AtomicInteger();
            for (Future<Optional<ReallocationSuggestion>> result : results) {
                if (result.get(5, TimeUnit.SECONDS).isPresent()) {
                    presentCount.incrementAndGet();
                }
            }
            assertThat(presentCount.get()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("onSettingChanged(PREVIEW_TTL_MINUTES) reconstruye el cache y descarta las sugerencias guardadas")
    void onSettingChangedDescartaSugerenciasGuardadas() {
        store.saveSuggestion(new ReallocationSuggestion("sug_test", 1L, List.of(10L), 5L));

        store.onSettingChanged(new SettingChangedEvent(SettingKey.PREVIEW_TTL_MINUTES));

        assertThat(store.takeSuggestion("sug_test")).isEmpty();
    }
}
