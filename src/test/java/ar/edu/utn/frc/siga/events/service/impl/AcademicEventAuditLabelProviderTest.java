package ar.edu.utn.frc.siga.events.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.events.model.AcademicEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AcademicEventAuditLabelProvider")
class AcademicEventAuditLabelProviderTest {

    @Mock
    private SubjectService subjectService;
    @Mock
    private CommissionService commissionService;

    private AcademicEventAuditLabelProvider provider;

    @BeforeEach
    void setUp() {
        provider = new AcademicEventAuditLabelProvider(new EventLabels(subjectService, commissionService));
    }

    private static AuditedRecord record(String id, Long subjectId, Long commissionId, String description) {
        Map<String, Object> values = new HashMap<>();
        values.put("subjectId", subjectId);
        values.put("commissionId", commissionId);
        values.put("description", description);
        return new AuditedRecord(id, values);
    }

    @Test
    @DisplayName("entityType es AcademicEvent")
    void entityType_isAcademicEvent() {
        assertThat(provider.entityType()).isEqualTo(AcademicEvent.class);
    }

    @Test
    @DisplayName("formato '{materia} {comisión}' y una sola llamada en lote a cada servicio")
    void labels_subjectAndCommission_oneBatchCallPerService() {
        when(subjectService.findByIdsIncludingDeactivated(any()))
                .thenReturn(List.of(new SubjectResponseDto(1L, 100, "Álgebra", null, null, false)));
        when(commissionService.findByIds(any())).thenReturn(List.of(new CommissionResponseDto(2L, "1K1", null)));

        Map<String, String> labels = provider.labels(List.of(
                record("10", 1L, 2L, null), record("11", 1L, 2L, null)));

        assertThat(labels).containsOnly(Map.entry("10", "Álgebra 1K1"), Map.entry("11", "Álgebra 1K1"));
        verify(subjectService, times(1)).findByIdsIncludingDeactivated(any());
        verify(commissionService, times(1)).findByIds(any());
    }

    @Test
    @DisplayName("evento único sin materia usa la descripción; sin descripción, la comisión")
    void labels_noSubject_fallsBackToDescriptionThenCommission() {
        when(commissionService.findByIds(any())).thenReturn(List.of(new CommissionResponseDto(2L, "1K1", null)));

        Map<String, String> labels = provider.labels(List.of(
                record("10", null, 2L, "Charla de ingreso"), record("11", null, 2L, null)));

        assertThat(labels).containsOnly(Map.entry("10", "Charla de ingreso"), Map.entry("11", "1K1"));
    }

    @Test
    @DisplayName("un registro sin ningún dato para armar la etiqueta no la tiene")
    void labels_noData_hasNoLabel() {
        assertThat(provider.labels(List.of(record("10", null, null, null)))).isEmpty();
    }

    @Test
    @DisplayName("un recordId no numérico se saltea sin romper el resto del lote")
    void labels_nonNumericRecordId_isSkippedWithoutBreakingOthers() {
        when(commissionService.findByIds(any())).thenReturn(List.of(new CommissionResponseDto(2L, "1K1", null)));

        Map<String, String> labels = provider.labels(List.of(
                record("abc", null, 2L, null), record("11", null, 2L, null)));

        assertThat(labels).containsExactly(Map.entry("11", "1K1"));
    }
}
