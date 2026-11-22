package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
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
@DisplayName("SubjectCommissionAuditLabelProvider")
class SubjectCommissionAuditLabelProviderTest {

    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private CommissionRepository commissionRepository;

    private SubjectCommissionAuditLabelProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SubjectCommissionAuditLabelProvider(subjectRepository, commissionRepository);
    }

    private static AuditedRecord record(String id, Long subjectId, Long commissionId) {
        Map<String, Object> values = new HashMap<>();
        values.put("subject", subjectId);
        values.put("commission", commissionId);
        return new AuditedRecord(id, values);
    }

    private static Subject subject(Long id, String name) {
        return Subject.builder().id(id).name(name).build();
    }

    private static Commission commission(Long id, String courseCode) {
        return Commission.builder().id(id).courseCode(courseCode).build();
    }

    @Test
    @DisplayName("entityType es SubjectCommission")
    void entityType_isSubjectCommission() {
        assertThat(provider.entityType()).isEqualTo(SubjectCommission.class);
    }

    @Test
    @DisplayName("formato '{materia} ({comisión})', no '{materia}, comisión {código}'")
    void labels_subjectAndCommission() {
        when(subjectRepository.findAllById(any())).thenReturn(List.of(subject(1L, "Análisis Matemático I")));
        when(commissionRepository.findAllById(any())).thenReturn(List.of(commission(2L, "1K1")));

        assertThat(provider.labels(List.of(record("9", 1L, 2L))))
                .containsExactly(Map.entry("9", "Análisis Matemático I (1K1)"));
    }

    @Test
    @DisplayName("una sola consulta de materias y otra de comisiones para todos los registros, con ids sin repetir")
    @SuppressWarnings("unchecked")
    void labels_severalRecords_oneBatchLookupEach() {
        when(subjectRepository.findAllById(any())).thenReturn(List.of(subject(1L, "Álgebra"), subject(3L, "Física I")));
        when(commissionRepository.findAllById(any())).thenReturn(List.of(commission(2L, "1K1"), commission(4L, "2K3")));

        Map<String, String> labels = provider.labels(List.of(
                record("1", 1L, 2L), record("2", 1L, 4L), record("3", 3L, 2L)));

        ArgumentCaptor<Collection<Long>> subjectIds = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Collection<Long>> commissionIds = ArgumentCaptor.forClass(Collection.class);
        verify(subjectRepository, times(1)).findAllById(subjectIds.capture());
        verify(commissionRepository, times(1)).findAllById(commissionIds.capture());
        assertThat(subjectIds.getValue()).containsExactlyInAnyOrder(1L, 3L);
        assertThat(commissionIds.getValue()).containsExactlyInAnyOrder(2L, 4L);
        assertThat(labels).containsOnly(Map.entry("1", "Álgebra (1K1)"), Map.entry("2", "Álgebra (2K3)"),
                Map.entry("3", "Física I (1K1)"));
    }

    @Test
    @DisplayName("con solo la materia o solo la comisión muestra lo que hay; sin ninguna no hay etiqueta")
    void labels_partialData_showsWhatExists() {
        when(subjectRepository.findAllById(any())).thenReturn(List.of(subject(1L, "Álgebra")));
        when(commissionRepository.findAllById(any())).thenReturn(List.of(commission(2L, "1K1")));

        assertThat(provider.labels(List.of(record("1", 1L, null), record("2", null, 2L), record("3", null, null))))
                .containsOnly(Map.entry("1", "Álgebra"), Map.entry("2", "1K1"));
    }

    @Test
    @DisplayName("sin ids de materia ni de comisión no consulta ningún repositorio")
    void labels_noIds_queriesNothing() {
        assertThat(provider.labels(List.of(record("1", null, null)))).isEmpty();

        verifyNoInteractions(subjectRepository, commissionRepository);
    }
}
