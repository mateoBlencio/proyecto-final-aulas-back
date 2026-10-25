package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommissionAuditLabelProvider")
class CommissionAuditLabelProviderTest {

    @Mock
    private AcademicPeriodRepository academicPeriodRepository;

    private CommissionAuditLabelProvider provider;

    @BeforeEach
    void setUp() {
        provider = new CommissionAuditLabelProvider(academicPeriodRepository);
    }

    private static AuditedRecord record(String id, String courseCode, Long periodId) {
        Map<String, Object> values = new HashMap<>();
        values.put("courseCode", courseCode);
        values.put("academicPeriod", periodId);
        return new AuditedRecord(id, values);
    }

    private static AcademicPeriod period(Long id, int year, int semester) {
        return AcademicPeriod.builder().id(id).year(year).semester(semester).build();
    }

    @Test
    @DisplayName("entityType es Commission")
    void entityType_isCommission() {
        assertThat(provider.entityType()).isEqualTo(Commission.class);
    }

    @Test
    @DisplayName("formato 'Comisión {código}, {año}/{cuatrimestre}'")
    void labels_courseCodeAndPeriod() {
        when(academicPeriodRepository.findAllById(any())).thenReturn(List.of(period(5L, 2026, 1)));

        assertThat(provider.labels(List.of(record("1", "5D1", 5L))))
                .containsExactly(Map.entry("1", "Comisión 5D1, 2026/1"));
    }

    @Test
    @DisplayName("una sola consulta de períodos para todos los registros, con ids sin repetir")
    @SuppressWarnings("unchecked")
    void labels_severalRecords_oneBatchLookup() {
        when(academicPeriodRepository.findAllById(any())).thenReturn(
                List.of(period(5L, 2026, 1), period(6L, 2026, 2)));

        Map<String, String> labels = provider.labels(List.of(
                record("1", "5D1", 5L), record("2", "5D2", 5L), record("3", "4K1", 6L)));

        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(academicPeriodRepository, times(1)).findAllById(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(5L, 6L);
        assertThat(labels).containsOnly(
                Map.entry("1", "Comisión 5D1, 2026/1"),
                Map.entry("2", "Comisión 5D2, 2026/1"),
                Map.entry("3", "Comisión 4K1, 2026/2"));
    }

    @Test
    @DisplayName("si no se encuentra el período muestra solo el código de curso")
    void labels_periodNotFound_showsOnlyCourseCode() {
        when(academicPeriodRepository.findAllById(any())).thenReturn(List.of());

        assertThat(provider.labels(List.of(record("1", "5D1", 5L)))).containsExactly(Map.entry("1", "Comisión 5D1"));
    }

    @Test
    @DisplayName("sin período en el estado no consulta el repositorio")
    void labels_noPeriodId_noLookup() {
        assertThat(provider.labels(List.of(record("1", "5D1", null)))).containsExactly(Map.entry("1", "Comisión 5D1"));

        verifyNoInteractions(academicPeriodRepository);
    }

    @Test
    @DisplayName("sin código de curso el registro no tiene etiqueta")
    void labels_noCourseCode_hasNoLabel() {
        when(academicPeriodRepository.findAllById(any())).thenReturn(List.of(period(5L, 2026, 1)));

        assertThat(provider.labels(List.of(record("1", null, 5L)))).isEmpty();
    }
}
