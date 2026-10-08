package ar.edu.utn.frc.siga.audit.mapper;

import ar.edu.utn.frc.siga.audit.dto.RevisionMetadata;
import ar.edu.utn.frc.siga.audit.dto.response.AuditLogEntryDto;
import ar.edu.utn.frc.siga.audit.dto.response.AuditLogEntryType;
import ar.edu.utn.frc.siga.audit.model.ActorType;
import ar.edu.utn.frc.siga.audit.model.RevisionKind;
import ar.edu.utn.frc.siga.audit.repository.AuditGroupRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuditLogEntryMapper")
class AuditLogEntryMapperTest {

    private static final LocalDateTime DATE = LocalDateTime.of(2026, 5, 4, 10, 30);

    private final AuditLogEntryMapper mapper = new AuditLogEntryMapperImpl();

    private static AuditGroupRow group(String operationId, String parentOperationId) {
        return new AuditGroupRow(operationId, parentOperationId, 7, DATE, "user@frc", ActorType.SYSTEM,
                "Liberación", 2, List.of("Asignación"), RevisionKind.DELETED, "Asignación", "9");
    }

    @Test
    @DisplayName("toOperation copia parentOperationId")
    void toOperationCopiesParentOperationId() {
        AuditLogEntryDto dto = mapper.toOperation(group("op-child", "op-parent"));

        assertThat(dto.type()).isEqualTo(AuditLogEntryType.OPERATION);
        assertThat(dto.operationId()).isEqualTo("op-child");
        assertThat(dto.parentOperationId()).isEqualTo("op-parent");
    }

    @Test
    @DisplayName("toOperation deja parentOperationId en null si la operación no tiene causa")
    void toOperationWithoutParentKeepsNull() {
        assertThat(mapper.toOperation(group("op-root", null)).parentOperationId()).isNull();
    }

    @Test
    @DisplayName("toTransaction deja parentOperationId en null aunque la fila lo traiga")
    void toTransactionLeavesParentNull() {
        AuditLogEntryDto dto = mapper.toTransaction(group(null, "op-parent"));

        assertThat(dto.type()).isEqualTo(AuditLogEntryType.TRANSACTION);
        assertThat(dto.parentOperationId()).isNull();
    }

    @Test
    @DisplayName("toChange deja parentOperationId en null")
    void toChangeLeavesParentNull() {
        RevisionMetadata metadata = new RevisionMetadata("9", 7, DATE, "user@frc", ActorType.HUMAN,
                RevisionKind.MODIFIED, "Descripción", "op-1");

        AuditLogEntryDto dto = mapper.toChange(metadata, "Asignación", List.of());

        assertThat(dto.type()).isEqualTo(AuditLogEntryType.CHANGE);
        assertThat(dto.parentOperationId()).isNull();
    }
}
