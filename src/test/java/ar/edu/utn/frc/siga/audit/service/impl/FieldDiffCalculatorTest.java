package ar.edu.utn.frc.siga.audit.service.impl;

import ar.edu.utn.frc.siga.audit.dto.response.FieldChangeDto;
import ar.edu.utn.frc.siga.audit.model.RevisionKind;
import ar.edu.utn.frc.siga.audit.repository.AuditRecordStateRepository.RecordStates;
import ar.edu.utn.frc.siga.audit.service.AuditedEntity;
import ar.edu.utn.frc.siga.audit.service.AuditedEntity.AuditedColumn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FieldDiffCalculator")
class FieldDiffCalculatorTest {

    private static final AuditedEntity ENTITY = new AuditedEntity(
            Object.class, "Occurrence", "Ocurrencia", "ocurrencia_aud", "id_ocurrencia", Long.class,
            List.of(new AuditedColumn("ocurrencia_aud", "fecha", "date"),
                    new AuditedColumn("ocurrencia_aud", "estado", "status"),
                    new AuditedColumn("ocurrencia_aud", "id_evento_academico", "event")));

    private static Map<String, Object> state(Object date, Object status, Object event) {
        Map<String, Object> values = new HashMap<>();
        values.put("date", date);
        values.put("status", status);
        values.put("event", event);
        return values;
    }

    private static final Map<String, Object> NO_PREVIOUS = state(null, null, null);

    @Test
    @DisplayName("CREATED lista los campos con valor, con old null")
    void createdListsNonNullFieldsWithNullOld() {
        RecordStates states = new RecordStates(state("2026-05-04", "NEEDS_ROOM", 7L), NO_PREVIOUS);

        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.CREATED, states)).containsExactly(
                new FieldChangeDto("date", null, "2026-05-04"),
                new FieldChangeDto("status", null, "NEEDS_ROOM"),
                new FieldChangeDto("event", null, "7"));
    }

    @Test
    @DisplayName("CREATED ignora la fila previa aunque venga cargada")
    void createdIgnoresPreviousState() {
        RecordStates states = new RecordStates(state("2026-05-04", "NEEDS_ROOM", 7L), state("X", "Y", 1L));

        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.CREATED, states))
                .extracting(FieldChangeDto::oldValue).containsOnlyNulls();
    }

    @Test
    @DisplayName("DELETED lista el último estado con new null")
    void deletedListsLastStateWithNullNew() {
        RecordStates states = new RecordStates(state("2026-05-04", "ROOM_RELEASED", 7L), state("2026-05-04", "NEEDS_ROOM", 7L));

        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.DELETED, states)).containsExactly(
                new FieldChangeDto("date", "2026-05-04", null),
                new FieldChangeDto("status", "ROOM_RELEASED", null),
                new FieldChangeDto("event", "7", null));
    }

    @Test
    @DisplayName("MODIFIED con un solo campo cambiado devuelve exactamente ese campo")
    void modifiedReturnsOnlyTheChangedField() {
        RecordStates states = new RecordStates(
                state("2026-05-04", "ROOM_RELEASED", 7L), state("2026-05-04", "NEEDS_ROOM", 7L));

        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.MODIFIED, states))
                .containsExactly(new FieldChangeDto("status", "NEEDS_ROOM", "ROOM_RELEASED"));
    }

    @Test
    @DisplayName("MODIFIED sin fila previa lista todos los campos con valor, con old null")
    void modifiedWithoutPreviousRowListsEveryNonNullField() {
        RecordStates states = new RecordStates(state("2026-05-04", "NEEDS_ROOM", 7L), NO_PREVIOUS);

        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.MODIFIED, states)).containsExactly(
                new FieldChangeDto("date", null, "2026-05-04"),
                new FieldChangeDto("status", null, "NEEDS_ROOM"),
                new FieldChangeDto("event", null, "7"));
    }

    @Test
    @DisplayName("MODIFIED sin cambios en campos auditados devuelve lista vacía, no null")
    void modifiedWithoutChangesReturnsEmptyList() {
        RecordStates states = new RecordStates(
                state("2026-05-04", "NEEDS_ROOM", 7L), state("2026-05-04", "NEEDS_ROOM", 7L));

        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.MODIFIED, states)).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("MODIFIED compara por valor: un Long igual en ambos lados no es un cambio")
    void modifiedComparesByValueNotIdentity() {
        RecordStates states = new RecordStates(
                state("2026-05-04", "NEEDS_ROOM", Long.valueOf(1000)), state("2026-05-04", "NEEDS_ROOM", Long.valueOf(1000)));

        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.MODIFIED, states)).isEmpty();
    }

    @Test
    @DisplayName("MODIFIED de null a valor y de valor a null genera item en ambos sentidos")
    void modifiedFromAndToNullGeneratesItems() {
        RecordStates cleared = new RecordStates(state("2026-05-04", null, 7L), state("2026-05-04", "NEEDS_ROOM", 7L));
        RecordStates filled = new RecordStates(state("2026-05-04", "NEEDS_ROOM", 7L), state("2026-05-04", null, 7L));

        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.MODIFIED, cleared))
                .containsExactly(new FieldChangeDto("status", "NEEDS_ROOM", null));
        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.MODIFIED, filled))
                .containsExactly(new FieldChangeDto("status", null, "NEEDS_ROOM"));
    }

    @Test
    @DisplayName("MODIFIED compara BigDecimal con compareTo: 1.0 contra 1.00 no es un cambio, 1.0 contra 2.0 sí")
    void modifiedComparesBigDecimalByValue() {
        AuditedEntity priced = new AuditedEntity(Object.class, "Priced", "Precio", "precio_aud", "id", Long.class,
                List.of(new AuditedColumn("precio_aud", "monto", "amount")));

        RecordStates sameValue = new RecordStates(Map.of("amount", new java.math.BigDecimal("1.00")),
                Map.of("amount", new java.math.BigDecimal("1.0")));
        RecordStates otherValue = new RecordStates(Map.of("amount", new java.math.BigDecimal("2.0")),
                Map.of("amount", new java.math.BigDecimal("1.0")));

        assertThat(FieldDiffCalculator.calculate(priced, RevisionKind.MODIFIED, sameValue)).isEmpty();
        assertThat(FieldDiffCalculator.calculate(priced, RevisionKind.MODIFIED, otherValue))
                .containsExactly(new FieldChangeDto("amount", "1.0", "2.0"));
    }

    @Test
    @DisplayName("el orden del resultado sigue el orden de columns, no el del mapa de estado")
    void resultFollowsColumnsOrder() {
        Map<String, Object> reversed = new java.util.LinkedHashMap<>();
        reversed.put("event", 7L);
        reversed.put("status", "NEEDS_ROOM");
        reversed.put("date", "2026-05-04");
        RecordStates states = new RecordStates(reversed, NO_PREVIOUS);

        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.CREATED, states))
                .extracting(FieldChangeDto::field).containsExactly("date", "status", "event");
    }

    @Test
    @DisplayName("null en ambos lados no genera item en CREATED, DELETED ni MODIFIED")
    void nullOnBothSidesProducesNoItem() {
        RecordStates states = new RecordStates(state("2026-05-04", null, null), state("2026-05-04", null, null));

        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.CREATED, states))
                .extracting(FieldChangeDto::field).containsExactly("date");
        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.DELETED, states))
                .extracting(FieldChangeDto::field).containsExactly("date");
        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.MODIFIED, states)).isEmpty();
    }

    @Test
    @DisplayName("las propiedades fuera de columns no aparecen aunque estén en el estado")
    void propertiesOutsideColumnsAreIgnored() {
        Map<String, Object> current = state("2026-05-04", "NEEDS_ROOM", 7L);
        current.put("passwordHash", "secret");

        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.CREATED, new RecordStates(current, NO_PREVIOUS)))
                .extracting(FieldChangeDto::field).doesNotContain("passwordHash");
    }
}
