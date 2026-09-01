package ar.edu.utn.frc.siga.audit.registry;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guarda de drift: fija el conjunto de entidades {@code @Audited} descubierto vía metamodelo.
 * Si se agrega o quita un {@code @Audited} sin actualizar esta expectativa (y el mapa de
 * etiquetas de {@link AuditedEntityRegistry}), este test falla.
 */
@DisplayName("AuditedEntityRegistry (drift guard)")
class AuditedEntityRegistryTest extends AbstractIntegrationTest {

    @Autowired
    private AuditedEntityRegistry registry;

    @Test
    @DisplayName("descubre exactamente los tipos raíz auditados, colapsando la herencia")
    void discoversExactlyTheExpectedRootAuditedTypes() {
        assertThat(registry.all()).extracting(AuditedEntity::jpaName)
                .containsExactlyInAnyOrder(
                        "Allocation", "User", "AcademicEvent", "Occurrence",
                        "RoomRequest", "RoomRequestItem", "RoomPreference", "Setting");
    }

    @Test
    @DisplayName("no expone subtipos cuya superclase ya es auditada")
    void doesNotExposeAuditedSubtypes() {
        assertThat(registry.all()).extracting(AuditedEntity::jpaName)
                .doesNotContain("RecurringEvent", "UniqueEvent");
    }

    @Test
    @DisplayName("cada tipo tiene una etiqueta de dominio propia")
    void everyTypeHasADomainLabel() {
        assertThat(registry.all()).allSatisfy(entity -> {
            assertThat(entity.label()).isNotBlank();
            assertThat(entity.label()).isNotEqualTo(entity.jpaName());
        });
    }
}
