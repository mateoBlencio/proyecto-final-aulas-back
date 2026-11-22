package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.settings.api.SettingChangedEvent;
import ar.edu.utn.frc.siga.settings.api.SettingsReader;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import org.junit.jupiter.api.Test;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomRequestSuggestionStoreTest {

    private static final RoomRequestSuggestion SUGGESTION = new RoomRequestSuggestion("sug_1", 7L, 1L, List.of(10L));

    @Test
    void saveYTakeDevuelvenLaMismaSugerencia() {
        RoomRequestSuggestionStore store = storeWithTtl(30);

        store.save(SUGGESTION);

        assertThat(store.take("sug_1")).contains(SUGGESTION);
    }

    @Test
    void takeConsumeLaSugerencia() {
        RoomRequestSuggestionStore store = storeWithTtl(30);
        store.save(SUGGESTION);

        store.take("sug_1");

        assertThat(store.take("sug_1")).isEmpty();
    }

    @Test
    void takeDeUnIdInexistenteDevuelveVacio() {
        assertThat(storeWithTtl(30).take("sug_inexistente")).isEmpty();
    }

    @Test
    void takeDeUnaSugerenciaNoAfectaALasDemas() {
        RoomRequestSuggestionStore store = storeWithTtl(30);
        RoomRequestSuggestion other = new RoomRequestSuggestion("sug_2", 8L, 1L, List.of(20L));
        store.save(SUGGESTION);
        store.save(other);

        store.take("sug_1");

        assertThat(store.take("sug_2")).contains(other);
    }

    @Test
    void leeElTtlDesdeLaConfiguracionEnMinutos() {
        SettingsReader settingsReader = mock(SettingsReader.class);
        when(settingsReader.getLong(SettingKey.PREVIEW_TTL_MINUTES)).thenReturn(30L);

        new RoomRequestSuggestionStore(settingsReader).init();

        verify(settingsReader).getLong(SettingKey.PREVIEW_TTL_MINUTES);
    }

    @Test
    void conTtlCeroLaSugerenciaExpiraDeInmediato() {
        RoomRequestSuggestionStore store = storeWithTtl(0);

        store.save(SUGGESTION);

        assertThat(store.take("sug_1")).isEmpty();
    }

    @Test
    void alCambiarElTtlEnCalienteSeAplicaSinDescartarLasSugerenciasVigentes() {
        SettingsReader settingsReader = mock(SettingsReader.class);
        when(settingsReader.getLong(SettingKey.PREVIEW_TTL_MINUTES)).thenReturn(30L, 60L);
        RoomRequestSuggestionStore store = new RoomRequestSuggestionStore(settingsReader);
        store.init();
        store.save(SUGGESTION);

        store.onSettingChanged(new SettingChangedEvent(SettingKey.PREVIEW_TTL_MINUTES));

        assertThat(store.take("sug_1")).contains(SUGGESTION);
    }

    @Test
    void alBajarElTtlEnCalienteLasSugerenciasExpiranConElNuevoValor() {
        SettingsReader settingsReader = mock(SettingsReader.class);
        when(settingsReader.getLong(SettingKey.PREVIEW_TTL_MINUTES)).thenReturn(30L, 0L);
        RoomRequestSuggestionStore store = new RoomRequestSuggestionStore(settingsReader);
        store.init();
        store.save(SUGGESTION);

        store.onSettingChanged(new SettingChangedEvent(SettingKey.PREVIEW_TTL_MINUTES));

        assertThat(store.take("sug_1")).isEmpty();
    }

    @Test
    void ignoraLosCambiosDeOtrosSettings() {
        SettingsReader settingsReader = mock(SettingsReader.class);
        when(settingsReader.getLong(SettingKey.PREVIEW_TTL_MINUTES)).thenReturn(30L, 0L);
        RoomRequestSuggestionStore store = new RoomRequestSuggestionStore(settingsReader);
        store.init();
        store.save(SUGGESTION);

        store.onSettingChanged(new SettingChangedEvent(SettingKey.PREVIEW_SUGGESTION_TIME_LIMIT_SECONDS));

        assertThat(store.take("sug_1")).contains(SUGGESTION);
    }

    @Test
    void conVariosHilosTomandoElMismoIdExactamenteUnoLaObtiene() throws Exception {
        RoomRequestSuggestionStore store = storeWithTtl(30);
        store.save(SUGGESTION);
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        try {
            List<Future<Optional<RoomRequestSuggestion>>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return store.take("sug_1");
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (Future<Optional<RoomRequestSuggestion>> f : futures) {
                if (f.get(5, TimeUnit.SECONDS).isPresent()) {
                    winners.incrementAndGet();
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(winners).hasValue(1);
    }

    private static RoomRequestSuggestionStore storeWithTtl(long minutes) {
        SettingsReader settingsReader = mock(SettingsReader.class);
        when(settingsReader.getLong(SettingKey.PREVIEW_TTL_MINUTES)).thenReturn(minutes);
        RoomRequestSuggestionStore store = new RoomRequestSuggestionStore(settingsReader);
        store.init();
        return store;
    }
}
