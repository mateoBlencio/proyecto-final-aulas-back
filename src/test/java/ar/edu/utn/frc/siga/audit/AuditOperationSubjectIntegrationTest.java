package ar.edu.utn.frc.siga.audit;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.audit.service.AuditedEntityRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("subject de @AuditOperation (integración)")
class AuditOperationSubjectIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AuditedEntityRegistry registry;

    @Test
    @DisplayName("el subject de cada @AuditOperation es el nombre simple de una entidad auditada del registro")
    void everySubjectIsARegisteredAuditedEntity() {
        Map<String, String> unknown = new TreeMap<>();
        AuditOperationCoverageTest.auditOperations().forEach((key, operation) -> {
            if (!operation.subject().isBlank() && registry.bySimpleName(operation.subject()).isEmpty()) {
                unknown.put(key, operation.subject());
            }
        });

        assertThat(unknown).as("subjects que el registro no conoce (el resumen caería al texto base)").isEmpty();
    }
}
