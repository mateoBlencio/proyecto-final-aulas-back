package ar.edu.utn.frc.siga.sysacad.internal.client.view;

import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSubjectCommission;
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
 * "Inscriptos" no es una vista de SysAcad: es una columna de HorariosComisionesCupos (la misma vista de
 * donde sale MateriaNombre/MateriaDictado para el mock de Materias), a grano (curso, materia) — el mismo
 * grano que materia_comision. No es un dato de materia ni de comisión por separado: la misma materia trae
 * distinta cantidad según la comisión, y la misma comisión trae distinta cantidad según la materia (ver
 * .claude/docs/integracion-sysacad.md). Mock temporal, mismo flag que SubjectMockViewFetcher porque sale
 * de las mismas planillas de origen.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "siga.sysacad", name = "materias-mock-enabled", havingValue = "true")
public class SubjectCommissionMockViewFetcher {

    private final Resource fixture;
    private final ObjectMapper objectMapper;

    public SubjectCommissionMockViewFetcher(@Value("classpath:sysacad/mock/materia_comision.json") Resource fixture,
            ObjectMapper objectMapper) {
        this.fixture = fixture;
        this.objectMapper = objectMapper;
    }

    public List<RawSubjectCommission> fetch() {
        try (InputStream in = fixture.getInputStream()) {
            List<RawSubjectCommission> rows = List.of(objectMapper.readValue(in, RawSubjectCommission[].class));
            log.warn("Usando MOCK de inscriptos por materia_comision ({} filas) — SysAcad no expone este dato todavía",
                    rows.size());
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el fixture mock de materia_comision", e);
        }
    }
}
