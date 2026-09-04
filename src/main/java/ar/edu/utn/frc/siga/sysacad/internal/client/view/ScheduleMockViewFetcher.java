package ar.edu.utn.frc.siga.sysacad.internal.client.view;

import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * TEMPORAL — sirve un fixture estático en lugar de la vista real {@code HorariosComisionesCupos}
 * ({@link ScheduleViewFetcher}). Existe para tener asignaciones durante el desarrollo mientras las vistas
 * reales no están completas/alineadas (los cursos con horario y los que son comisión vigente hoy no se
 * solapan). Devuelve exactamente el mismo tipo ({@code List<RawSchedule>}) que el fetcher real, así el
 * reemplazo es apagar {@code siga.sysacad.asignaciones-mock-enabled} (o borrar esta clase + el fixture):
 * {@link ar.edu.utn.frc.siga.sysacad.internal.service.impl.SysacadCatalogReaderImpl} vuelve a la vista real
 * sin más cambios.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "siga.sysacad", name = "asignaciones-mock-enabled", havingValue = "true")
public class ScheduleMockViewFetcher {

    private final Resource fixture;
    private final ObjectMapper objectMapper;

    public ScheduleMockViewFetcher(@Value("classpath:sysacad/mock/asignaciones.json") Resource fixture,
            ObjectMapper objectMapper) {
        this.fixture = fixture;
        this.objectMapper = objectMapper;
    }

    public List<RawSchedule> fetch() {
        try (InputStream in = fixture.getInputStream()) {
            List<RawSchedule> rows = List.of(objectMapper.readValue(in, RawSchedule[].class));
            log.warn("Usando MOCK de asignaciones (HorariosComisionesCupos) ({} filas) — temporal, hasta usar "
                    + "la vista real", rows.size());
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el fixture mock de asignaciones", e);
        }
    }
}
