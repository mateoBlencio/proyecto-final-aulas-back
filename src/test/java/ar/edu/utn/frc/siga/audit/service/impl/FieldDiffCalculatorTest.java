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
            List.of(new AuditedColumn("ocurrencia_aud", "fecha", "date", null, null),
                    new AuditedColumn("ocurrencia_aud", "estado", "status", null, null),
                    new AuditedColumn("ocurrencia_aud", "id_evento_academico", "event", null, null)));

    private static Map<String, Object> state(Object date, Object status, Object event) {
        Map<String, Object> values = new HashMap<>();
        values.put("date", date);
        values.put("status", status);
        values.put("event", event);
        return values;
    }

    private static final FieldValueFormatter PLAIN = FieldValueFormatter.withoutReferences();
    private static final Map<String, Object> NO_PREVIOUS = state(null, null, null);

    @Test
    @DisplayName("CREATED lista los campos con valor, con old null")
    void createdListsNonNullFieldsWithNullOld() {
        RecordStates states = new RecordStates(state("2026-05-04", "NEEDS_ROOM", 7L), NO_PREVIOUS);

        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.CREATED, states, PLAIN)).containsExactly(
                new FieldChangeDto("date", "Fecha", null, "2026-05-04"),
                new FieldChangeDto("status", "Estado", null, "NEEDS_ROOM"),
                new FieldChangeDto("event", "Evento", null, "7"));
    }

    @Test
    @DisplayName("CREATED ignora la fila previa aunque venga cargada")
    void createdIgnoresPreviousState() {
        RecordStates states = new RecordStates(state("2026-05-04", "NEEDS_ROOM", 7L), state("X", "Y", 1L));

        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.CREATED, states, PLAIN))
                .extracting(FieldChangeDto::oldValue).containsOnlyNulls();
    }

    @Test
    @DisplayName("DELETED lista el último estado con new null")
    void deletedListsLastStateWithNullNew() {
        RecordStates states = new RecordStates(state("2026-05-04", "ROOM_RELEASED", 7L), state("2026-05-04", "NEEDS_ROOM", 7L));

        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.DELETED, states, PLAIN)).containsExactly(
                new FieldChangeDto("date", "Fecha", "2026-05-04", null),
                new FieldChangeDto("status", "Estado", "ROOM_RELEASED", null),
                new FieldChangeDto("event", "Evento", "7", null));
    }

    @Test
    @DisplayName("MODIFIED con un solo campo cambiado devuelve exactamente ese campo")
    void modifiedReturnsOnlyTheChangedField() {
        RecordStates states = new RecordStates(
                state("2026-05-04", "ROOM_RELEASED", 7L), state("2026-05-04", "NEEDS_ROOM", 7L));

        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.MODIFIED, states, PLAIN))
                .containsExactly(new FieldChangeDto("status", "Estado", "NEEDS_ROOM", "ROOM_RELEASED"));
    }

    @Test
    @DisplayName("MODIFIED sin fila previa lista todos los campos con valor, con old null")
    void modifiedWithoutPreviousRowListsEveryNonNullField() {
        RecordStates states = new RecordStates(state("2026-05-04", "NEEDS_ROOM", 7L), NO_PREVIOUS);

        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.MODIFIED, states, PLAIN)).containsExactly(
                new FieldChangeDto("date", "Fecha", null, "2026-05-04"),
                new FieldChangeDto("status", "Estado", null, "NEEDS_ROOM"),
                new FieldChangeDto("event", "Evento", null, "7"));
    }

    @Test
    @DisplayName("MODIFIED sin cambios en campos auditados devuelve lista vacía, no null")
    void modifiedWithoutChangesReturnsEmptyList() {
        RecordStates states = new RecordStates(
                state("2026-05-04", "NEEDS_ROOM", 7L), state("2026-05-04", "NEEDS_ROOM", 7L));

        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.MODIFIED, states, PLAIN)).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("MODIFIED compara por valor: un Long igual en ambos lados no es un cambio")
    void modifiedComparesByValueNotIdentity() {
        RecordStates states = new RecordStates(
                state("2026-05-04", "NEEDS_ROOM", Long.valueOf(1000)), state("2026-05-04", "NEEDS_ROOM", Long.valueOf(1000)));

        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.MODIFIED, states, PLAIN)).isEmpty();
    }

    @Test
    @DisplayName("MODIFIED de null a valor y de valor a null genera item en ambos sentidos")
    void modifiedFromAndToNullGeneratesItems() {
        RecordStates cleared = new RecordStates(state("2026-05-04", null, 7L), state("2026-05-04", "NEEDS_ROOM", 7L));
        RecordStates filled = new RecordStates(state("2026-05-04", "NEEDS_ROOM", 7L), state("2026-05-04", null, 7L));

        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.MODIFIED, cleared, PLAIN))
                .containsExactly(new FieldChangeDto("status", "Estado", "NEEDS_ROOM", null));
        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.MODIFIED, filled, PLAIN))
                .containsExactly(new FieldChangeDto("status", "Estado", null, "NEEDS_ROOM"));
    }

    @Test
    @DisplayName("MODIFIED compara BigDecimal con compareTo: 1.0 contra 1.00 no es un cambio, 1.0 contra 2.0 sí")
    void modifiedComparesBigDecimalByValue() {
        AuditedEntity priced = new AuditedEntity(Object.class, "Priced", "Precio", "precio_aud", "id", Long.class,
                List.of(new AuditedColumn("precio_aud", "monto", "amount", null, null)));

        RecordStates sameValue = new RecordStates(Map.of("amount", new java.math.BigDecimal("1.00")),
                Map.of("amount", new java.math.BigDecimal("1.0")));
        RecordStates otherValue = new RecordStates(Map.of("amount", new java.math.BigDecimal("2.0")),
                Map.of("amount", new java.math.BigDecimal("1.0")));

        assertThat(FieldDiffCalculator.calculate(priced, RevisionKind.MODIFIED, sameValue, PLAIN)).isEmpty();
        assertThat(FieldDiffCalculator.calculate(priced, RevisionKind.MODIFIED, otherValue, PLAIN))
                .containsExactly(new FieldChangeDto("amount", "Amount", "1.0", "2.0"));
    }

    @Test
    @DisplayName("el orden del resultado sigue el orden de columns, no el del mapa de estado")
    void resultFollowsColumnsOrder() {
        Map<String, Object> reversed = new java.util.LinkedHashMap<>();
        reversed.put("event", 7L);
        reversed.put("status", "NEEDS_ROOM");
        reversed.put("date", "2026-05-04");
        RecordStates states = new RecordStates(reversed, NO_PREVIOUS);

        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.CREATED, states, PLAIN))
                .extracting(FieldChangeDto::field).containsExactly("date", "status", "event");
    }

    @Test
    @DisplayName("null en ambos lados no genera item en CREATED, DELETED ni MODIFIED")
    void nullOnBothSidesProducesNoItem() {
        RecordStates states = new RecordStates(state("2026-05-04", null, null), state("2026-05-04", null, null));

        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.CREATED, states, PLAIN))
                .extracting(FieldChangeDto::field).containsExactly("date");
        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.DELETED, states, PLAIN))
                .extracting(FieldChangeDto::field).containsExactly("date");
        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.MODIFIED, states, PLAIN)).isEmpty();
    }

    @Test
    @DisplayName("las propiedades fuera de columns no aparecen aunque estén en el estado")
    void propertiesOutsideColumnsAreIgnored() {
        Map<String, Object> current = state("2026-05-04", "NEEDS_ROOM", 7L);
        current.put("passwordHash", "secret");

        assertThat(FieldDiffCalculator.calculate(ENTITY, RevisionKind.CREATED, new RecordStates(current, NO_PREVIOUS), PLAIN))
                .extracting(FieldChangeDto::field).doesNotContain("passwordHash");
    }

    @Test
    @DisplayName("formatea para una persona: Sí/No, fechas dd/MM/yyyy, horas, enums traducidos y etiqueta del campo")
    void formatsValuesForAPerson() {
        AuditedEntity entity = new AuditedEntity(Object.class, "Item", "Ítem", "item_aud", "id", Long.class, List.of(
                new AuditedColumn("item_aud", "proyector", "requiresProjector", Boolean.class, null),
                new AuditedColumn("item_aud", "fecha", "date", java.time.LocalDate.class, null),
                new AuditedColumn("item_aud", "hora", "startTime", java.time.LocalTime.class, null),
                new AuditedColumn("item_aud", "estado", "status", java.time.DayOfWeek.class, null),
                new AuditedColumn("item_aud", "otro", "requiredSoftware", String.class, null)));
        Map<String, Object> current = new HashMap<>();
        current.put("requiresProjector", true);
        current.put("date", java.time.LocalDate.of(2026, 11, 3));
        current.put("startTime", java.time.LocalTime.of(13, 15));
        current.put("status", "TUESDAY");
        current.put("requiredSoftware", "NEEDS_ROOM");
        Map<String, Object> previous = new HashMap<>();
        previous.put("requiresProjector", false);

        assertThat(FieldDiffCalculator.calculate(entity, RevisionKind.MODIFIED, new RecordStates(current, previous), PLAIN))
                .containsExactly(
                        new FieldChangeDto("requiresProjector", "Requiere proyector", "No", "Sí"),
                        new FieldChangeDto("date", "Fecha", null, "03/11/2026"),
                        new FieldChangeDto("startTime", "Hora de inicio", null, "13:15"),
                        new FieldChangeDto("status", "Estado", null, "Martes"),
                        new FieldChangeDto("requiredSoftware", "Software requerido", null, "NEEDS_ROOM"));
    }

    @Test
    @DisplayName("una referencia se muestra con la etiqueta precargada y, si no la hay, con el id")
    void showsReferenceLabelOrFallsBackToId() {
        AuditedEntity entity = new AuditedEntity(Object.class, "Allocation", "Asignación", "asig_aud", "id", Long.class,
                List.of(new AuditedColumn("asig_aud", "id_aula", "classroomId", Long.class, String.class)));
        FieldValueFormatter formatter = new FieldValueFormatter(
                Map.of(new FieldValueFormatter.ReferenceKey(String.class, "87"), "Aula 220, Central"));
        RecordStates states = new RecordStates(Map.of("classroomId", 88L), Map.of("classroomId", 87L));

        assertThat(FieldDiffCalculator.calculate(entity, RevisionKind.MODIFIED, states, formatter)).containsExactly(
                new FieldChangeDto("classroomId", "Aula", "Aula 220, Central", "88"));
    }
}
