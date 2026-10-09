package ar.edu.utn.frc.siga.audit.repository;

import ar.edu.utn.frc.siga.audit.service.AuditedEntity;
import ar.edu.utn.frc.siga.audit.service.AuditedEntityRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@link ArchivedTable} names tables by hand because {@code audit} cannot import the entities: the registry backs it up. */
@DisplayName("AuditArchiveRepository: validación de ArchivedTable al arrancar")
class AuditArchiveRepositoryStartupTest {

    private static AuditedEntity entity(String auditTable, String idColumn) {
        return new AuditedEntity(Object.class, auditTable, auditTable, auditTable, idColumn, Long.class, List.of());
    }

    private static AuditArchiveRepository repositoryOver(AuditedEntity... entities) {
        AuditedEntityRegistry registry = mock(AuditedEntityRegistry.class);
        when(registry.all()).thenReturn(List.of(entities));
        return new AuditArchiveRepository(mock(NamedParameterJdbcTemplate.class), registry);
    }

    @Test
    @DisplayName("arranca cuando el registry tiene las dos tablas con el mismo idColumn")
    void startsWhenRegistryMatches() {
        AuditArchiveRepository repository = repositoryOver(
                entity("ocurrencia_aud", "id_ocurrencia"), entity("asignacion_aula_aud", "id_asignacion"),
                entity("configuracion_aud", "clave"));

        assertThatCode(repository::buildStatements).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("falla si el registry no audita una de las tablas archivadas")
    void failsWhenATableIsNotAudited() {
        AuditArchiveRepository repository = repositoryOver(entity("ocurrencia_aud", "id_ocurrencia"));

        assertThatThrownBy(repository::buildStatements)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ALLOCATION")
                .hasMessageContaining("asignacion_aula_aud.id_asignacion");
    }

    @Test
    @DisplayName("falla si la tabla coincide pero el idColumn del registry es otro")
    void failsWhenIdColumnDiffers() {
        AuditArchiveRepository repository = repositoryOver(
                entity("ocurrencia_aud", "id_otro"), entity("asignacion_aula_aud", "id_asignacion"));

        assertThatThrownBy(repository::buildStatements)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OCCURRENCE");
    }

    @Test
    @DisplayName("falla si el registry está vacío")
    void failsWithEmptyRegistry() {
        assertThatThrownBy(repositoryOver()::buildStatements).isInstanceOf(IllegalStateException.class);
    }
}
