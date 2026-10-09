package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RoomRequestAuditLabelProvider")
class RoomRequestAuditLabelProviderTest {

    private final RoomRequestAuditLabelProvider provider = new RoomRequestAuditLabelProvider();

    @Test
    @DisplayName("entityType es RoomRequest")
    void entityType_isRoomRequest() {
        assertThat(provider.entityType()).isEqualTo(RoomRequest.class);
    }

    @Test
    @DisplayName("formato 'Solicitud de {docente}'")
    void labels_usesTeacherName() {
        Map<String, String> labels = provider.labels(List.of(
                new AuditedRecord("1", Map.of("teacherName", "Juan Pérez")),
                new AuditedRecord("2", Map.of("teacherName", "Ada Lovelace"))));

        assertThat(labels).containsOnly(
                Map.entry("1", "Solicitud de Juan Pérez"), Map.entry("2", "Solicitud de Ada Lovelace"));
    }

    @Test
    @DisplayName("sin nombre de docente (null o ausente) el registro no tiene etiqueta")
    void labels_missingTeacherName_hasNoLabel() {
        Map<String, Object> nullName = new HashMap<>();
        nullName.put("teacherName", null);

        assertThat(provider.labels(List.of(new AuditedRecord("1", nullName), new AuditedRecord("2", Map.of()))))
                .isEmpty();
    }
}
