package ar.edu.utn.frc.siga.ingest.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IngestCache")
class IngestCacheTest {

    @Test
    @DisplayName("get: primera llamada invoca el loader y cachea el resultado")
    void primeraLlamadaInvocaElLoader() {
        IngestCache cache = new IngestCache();
        AtomicInteger calls = new AtomicInteger();

        String value = cache.get(String.class, "k1", () -> {
            calls.incrementAndGet();
            return "valor";
        });

        assertThat(value).isEqualTo("valor");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("get: segunda llamada con la misma clave no vuelve a invocar el loader")
    void segundaLlamadaConMismaClaveNoReinvoca() {
        IngestCache cache = new IngestCache();
        AtomicInteger calls = new AtomicInteger();

        cache.get(String.class, "k1", () -> {
            calls.incrementAndGet();
            return "valor";
        });
        cache.get(String.class, "k1", () -> {
            calls.incrementAndGet();
            return "otro valor";
        });

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("get: misma clave pero distinto tipo no comparte entrada")
    void mismaClaveDistintoTipoNoComparteEntrada() {
        IngestCache cache = new IngestCache();

        String asString = cache.get(String.class, 1, () -> "string");
        Integer asInt = cache.get(Integer.class, 1, () -> 99);

        assertThat(asString).isEqualTo("string");
        assertThat(asInt).isEqualTo(99);
    }

    @Test
    @DisplayName("get: distinta clave, mismo tipo, invoca el loader por separado")
    void distintaClaveInvocaPorSeparado() {
        IngestCache cache = new IngestCache();
        AtomicInteger calls = new AtomicInteger();

        cache.get(String.class, "k1", () -> {
            calls.incrementAndGet();
            return "a";
        });
        cache.get(String.class, "k2", () -> {
            calls.incrementAndGet();
            return "b";
        });

        assertThat(calls.get()).isEqualTo(2);
    }
}
