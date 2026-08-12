package ar.edu.utn.frc.siga.ingest.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IngestCache")
class IngestCacheTest {

    private final IngestCache cache = new IngestCache();

    @Test
    @DisplayName("misma clave: el loader se invoca una sola vez y se devuelve el valor cacheado")
    void computeIfAbsentDedupeaPorClave() {
        AtomicInteger calls = new AtomicInteger();
        SpecialtyResponseDto expected = new SpecialtyResponseDto(1, "Ingeniería en Sistemas");

        SpecialtyResponseDto first = cache.get(SpecialtyResponseDto.class, 1, () -> {
            calls.incrementAndGet();
            return expected;
        });
        SpecialtyResponseDto second = cache.get(SpecialtyResponseDto.class, 1, () -> {
            calls.incrementAndGet();
            return new SpecialtyResponseDto(1, "no debería llamarse");
        });

        assertThat(first).isEqualTo(expected);
        assertThat(second).isEqualTo(expected);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("claves distintas: el loader se invoca una vez por cada clave")
    void clavesDistintasNoComparteCache() {
        AtomicInteger calls = new AtomicInteger();

        cache.get(SpecialtyResponseDto.class, 1, () -> {
            calls.incrementAndGet();
            return new SpecialtyResponseDto(1, "Ingeniería en Sistemas");
        });
        cache.get(SpecialtyResponseDto.class, 2, () -> {
            calls.incrementAndGet();
            return new SpecialtyResponseDto(2, "Ingeniería Civil");
        });

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("tipos distintos con la misma clave no colisionan (namespace por Class)")
    void tiposDistintosNoColisionan() {
        cache.get(SpecialtyResponseDto.class, "1", () -> new SpecialtyResponseDto(1, "Ingeniería en Sistemas"));

        AtomicInteger buildingCalls = new AtomicInteger();
        cache.get(BuildingResponseDto.class, "1", () -> {
            buildingCalls.incrementAndGet();
            return new BuildingResponseDto(1, "Edificio Central", 5, true);
        });

        assertThat(buildingCalls.get()).isEqualTo(1);
    }
}
