package ar.edu.utn.frc.siga.audit.repository;

import ar.edu.utn.frc.siga.audit.service.AuditedEntity;
import ar.edu.utn.frc.siga.common.exception.InvalidSelectionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ChangeScope.ofRecord")
class ChangeScopeTest {

    private static AuditedEntity entityWithId(String label, Class<?> idType) {
        return new AuditedEntity(Object.class, "Any", label, "any_aud", "id_any", idType, List.of());
    }

    private static final AuditedEntity LONG_ID = entityWithId("Ocurrencia", Long.class);
    private static final AuditedEntity INTEGER_ID = entityWithId("Entero", Integer.class);
    private static final AuditedEntity UUID_ID = entityWithId("Uuid", UUID.class);
    private static final AuditedEntity STRING_ID = entityWithId("Configuración", String.class);

    @ParameterizedTest(name = "[{index}] id Long \"{0}\"")
    @ValueSource(strings = {"abc", "", " 7", "7.5", "99999999999999999999"})
    @DisplayName("un id Long no convertible lanza InvalidSelectionException con la etiqueta y el valor")
    void longIdNotConvertible_throwsInvalidSelection(String recordId) {
        assertThatThrownBy(() -> ChangeScope.ofRecord(LONG_ID, recordId))
                .isInstanceOf(InvalidSelectionException.class)
                .hasMessageContaining("Ocurrencia")
                .hasMessageContaining("'" + recordId + "'");
    }

    @ParameterizedTest(name = "[{index}] id Integer \"{0}\"")
    @ValueSource(strings = {"abc", "2147483648"})
    @DisplayName("un id Integer no convertible (texto o fuera de rango) lanza InvalidSelectionException")
    void integerIdNotConvertible_throwsInvalidSelection(String recordId) {
        assertThatThrownBy(() -> ChangeScope.ofRecord(INTEGER_ID, recordId))
                .isInstanceOf(InvalidSelectionException.class);
    }

    @Test
    @DisplayName("un id UUID mal formado lanza InvalidSelectionException")
    void malformedUuid_throwsInvalidSelection() {
        assertThatThrownBy(() -> ChangeScope.ofRecord(UUID_ID, "no-es-un-uuid"))
                .isInstanceOf(InvalidSelectionException.class);
    }

    @Test
    @DisplayName("un id Long válido se convierte a Long y deja nulos operationId y revision")
    void longIdValid_convertedToLong() {
        ChangeScope scope = ChangeScope.ofRecord(LONG_ID, "42");

        assertThat(scope.recordId()).isEqualTo(42L);
        assertThat(scope.entity()).isSameAs(LONG_ID);
        assertThat(scope.operationId()).isNull();
        assertThat(scope.revision()).isNull();
    }

    @Test
    @DisplayName("el límite de Long (Long.MAX_VALUE) se acepta y Long.MAX_VALUE + 1 se rechaza")
    void longBoundary() {
        assertThat(ChangeScope.ofRecord(LONG_ID, String.valueOf(Long.MAX_VALUE)).recordId()).isEqualTo(Long.MAX_VALUE);
        assertThatThrownBy(() -> ChangeScope.ofRecord(LONG_ID, "9223372036854775808"))
                .isInstanceOf(InvalidSelectionException.class);
    }

    @Test
    @DisplayName("un id String acepta cualquier texto sin conversión, incluso 'abc' y puntos")
    void stringId_acceptsAnyText() {
        assertThat(ChangeScope.ofRecord(STRING_ID, "abc").recordId()).isEqualTo("abc");
        assertThat(ChangeScope.ofRecord(STRING_ID, "events.hours.end").recordId()).isEqualTo("events.hours.end");
    }

    @Test
    @DisplayName("un id UUID válido se convierte a UUID")
    void uuidValid_convertedToUuid() {
        UUID id = UUID.randomUUID();

        assertThat(ChangeScope.ofRecord(UUID_ID, id.toString()).recordId()).isEqualTo(id);
    }

    @Test
    @DisplayName("el constructor acepta exactamente las tres formas: operación, revisión o entidad con id")
    void constructor_acceptsTheThreeValidShapes() {
        assertThat(new ChangeScope("op", null, null, null).operationId()).isEqualTo("op");
        assertThat(new ChangeScope(null, 3, null, null).revision()).isEqualTo(3);
        assertThat(new ChangeScope(null, null, LONG_ID, 1L).recordId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("el constructor rechaza ninguna forma, dos formas mezcladas y entidad sin id (o id sin entidad)")
    void constructor_rejectsInvalidCombinations() {
        assertThatThrownBy(() -> new ChangeScope(null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChangeScope("op", 3, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChangeScope("op", null, LONG_ID, 1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChangeScope(null, 3, LONG_ID, 1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChangeScope(null, null, LONG_ID, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChangeScope(null, null, null, 1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChangeScope("op", null, null, 1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChangeScope(null, 3, null, 1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChangeScope("op", 3, LONG_ID, 1L)).isInstanceOf(IllegalArgumentException.class);
    }
}
