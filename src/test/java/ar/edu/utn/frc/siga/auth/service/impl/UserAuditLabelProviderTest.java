package ar.edu.utn.frc.siga.auth.service.impl;

import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.auth.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserAuditLabelProvider")
class UserAuditLabelProviderTest {

    private final UserAuditLabelProvider provider = new UserAuditLabelProvider();

    @Test
    @DisplayName("entityType es User")
    void entityType_isUser() {
        assertThat(provider.entityType()).isEqualTo(User.class);
    }

    @Test
    @DisplayName("la etiqueta es el email del estado auditado, uno por registro")
    void labels_usesEmailOfEachRecord() {
        Map<String, String> labels = provider.labels(List.of(
                new AuditedRecord("1", Map.of("email", "ana@frc.utn.edu.ar")),
                new AuditedRecord("2", Map.of("email", "luis@frc.utn.edu.ar"))));

        assertThat(labels).containsOnly(
                Map.entry("1", "ana@frc.utn.edu.ar"), Map.entry("2", "luis@frc.utn.edu.ar"));
    }

    @Test
    @DisplayName("un registro sin email (null o ausente) no tiene etiqueta")
    void labels_missingEmail_hasNoLabel() {
        Map<String, Object> nullEmail = new HashMap<>();
        nullEmail.put("email", null);

        Map<String, String> labels = provider.labels(List.of(
                new AuditedRecord("1", nullEmail), new AuditedRecord("2", Map.of())));

        assertThat(labels).isEmpty();
    }
}
