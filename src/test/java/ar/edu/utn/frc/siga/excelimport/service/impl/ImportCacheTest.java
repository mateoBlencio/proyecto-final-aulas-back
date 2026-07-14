package ar.edu.utn.frc.siga.excelimport.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ImportCache")
class ImportCacheTest {

    private final ImportCache cache = new ImportCache();

    @Test
    @DisplayName("misma clave: el loader se invoca una sola vez y se devuelve el valor cacheado")
    void computeIfAbsentDedupeaPorClave() {
        AtomicInteger calls = new AtomicInteger();
        SpecialtyResponseDto expected = new SpecialtyResponseDto(1, "Ingeniería en Sistemas");

        SpecialtyResponseDto first = cache.getSpecialty(1, () -> {
            calls.incrementAndGet();
            return expected;
        });
        SpecialtyResponseDto second = cache.getSpecialty(1, () -> {
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

        cache.getSpecialty(1, () -> {
            calls.incrementAndGet();
            return new SpecialtyResponseDto(1, "Ingeniería en Sistemas");
        });
        cache.getSpecialty(2, () -> {
            calls.incrementAndGet();
            return new SpecialtyResponseDto(2, "Ingeniería Civil");
        });

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("los distintos mapas del cache (specialty vs building) no comparten namespace de claves")
    void mapasDistintosNoColisionan() {
        cache.getSpecialty(1, () -> new SpecialtyResponseDto(1, "Ingeniería en Sistemas"));

        AtomicInteger buildingCalls = new AtomicInteger();
        cache.getBuilding("1", () -> {
            buildingCalls.incrementAndGet();
            return new BuildingResponseDto(1, "Edificio Central", 5, true);
        });

        assertThat(buildingCalls.get()).isEqualTo(1);
    }
}
