package ar.edu.utn.frc.siga.sysacad.internal.client.view;

import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSubject;
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
 * SysAcad no expone todavía una vista de catálogo de materias (ver .claude/docs/integracion-sysacad.md).
 * Mientras tanto, esto sirve un fixture estático armado a partir de datos ya vistos en otras vistas/planillas,
 * para poder probar el resto del pipeline de sync (Subject, materia_comision). Reemplazar por un fetcher real
 * (mismo patrón que SpecialtyViewFetcher/CommissionViewFetcher) en cuanto SysAcad publique la vista.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "siga.sysacad", name = "materias-mock-enabled", havingValue = "true")
public class SubjectMockViewFetcher {

    private final Resource fixture;
    private final ObjectMapper objectMapper;

    public SubjectMockViewFetcher(@Value("classpath:sysacad/mock/materias.json") Resource fixture,
            ObjectMapper objectMapper) {
        this.fixture = fixture;
        this.objectMapper = objectMapper;
    }

    public List<RawSubject> fetch() {
        try (InputStream in = fixture.getInputStream()) {
            List<RawSubject> rows = List.of(objectMapper.readValue(in, RawSubject[].class));
            log.warn("Usando catálogo MOCK de materias ({} filas) — SysAcad todavía no expone esta vista", rows.size());
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el fixture mock de materias", e);
        }
    }
}
