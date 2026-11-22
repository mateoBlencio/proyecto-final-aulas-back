package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AcademicPeriodAuditLabelProvider")
class AcademicPeriodAuditLabelProviderTest {

    private final AcademicPeriodAuditLabelProvider provider = new AcademicPeriodAuditLabelProvider();

    private static AuditedRecord record(String id, Integer year, Integer semester) {
        Map<String, Object> values = new HashMap<>();
        values.put("year", year);
        values.put("semester", semester);
        return new AuditedRecord(id, values);
    }

    @Test
    @DisplayName("entityType es AcademicPeriod")
    void entityType_isAcademicPeriod() {
        assertThat(provider.entityType()).isEqualTo(AcademicPeriod.class);
    }

    @Test
    @DisplayName("formato '{año}/{cuatrimestre}'")
    void labels_yearAndSemester() {
        assertThat(provider.labels(List.of(record("1", 2026, 1), record("2", 2025, 2))))
                .containsOnly(Map.entry("1", "2026/1"), Map.entry("2", "2025/2"));
    }

    @Test
    @DisplayName("sin año o sin cuatrimestre el registro no tiene etiqueta")
    void labels_missingYearOrSemester_hasNoLabel() {
        assertThat(provider.labels(List.of(record("1", null, 1), record("2", 2026, null)))).isEmpty();
    }
}
