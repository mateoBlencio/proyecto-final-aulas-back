package ar.edu.utn.frc.siga.audit.service.impl;

import ar.edu.utn.frc.siga.audit.dto.response.FieldChangeDto;
import ar.edu.utn.frc.siga.audit.model.RevisionKind;
import ar.edu.utn.frc.siga.audit.repository.AuditRecordStateRepository.RecordStates;
import ar.edu.utn.frc.siga.audit.service.AuditedEntity;
import ar.edu.utn.frc.siga.audit.service.AuditedEntity.AuditedColumn;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Field-level diff of an audited record revision, in the order of {@link AuditedEntity#columns()}. */
public final class FieldDiffCalculator {

    private FieldDiffCalculator() {
    }

    /**
     * {@code CREATED}: every non-null value. {@code DELETED}: every non-null value of the last state
     * (Envers keeps it with {@code store_data_at_delete}). {@code MODIFIED}: the properties that differ from
     * the previous audit row; with no previous row, every non-null value.
     */
    public static List<FieldChangeDto> calculate(AuditedEntity entity, RevisionKind kind, RecordStates states) {
        List<FieldChangeDto> changes = new ArrayList<>();
        for (AuditedColumn column : entity.columns()) {
            Object current = states.current().get(column.property());
            Object previous = states.previous().get(column.property());
            switch (kind) {
                case CREATED -> add(changes, column, null, current);
                case DELETED -> add(changes, column, current, null);
                case MODIFIED -> {
                    if (differs(previous, current)) {
                        add(changes, column, previous, current);
                    }
                }
            }
        }
        return changes;
    }

    // BigDecimal.equals compares scale, so 1.0 and 1.00 would count as a change.
    private static boolean differs(Object previous, Object current) {
        if (previous instanceof BigDecimal a && current instanceof BigDecimal b) {
            return a.compareTo(b) != 0;
        }
        return !Objects.equals(previous, current);
    }

    private static void add(List<FieldChangeDto> changes, AuditedColumn column, Object oldValue, Object newValue) {
        if (oldValue != null || newValue != null) {
            changes.add(new FieldChangeDto(column.property(),
                    oldValue == null ? null : oldValue.toString(),
                    newValue == null ? null : newValue.toString()));
        }
    }
}
