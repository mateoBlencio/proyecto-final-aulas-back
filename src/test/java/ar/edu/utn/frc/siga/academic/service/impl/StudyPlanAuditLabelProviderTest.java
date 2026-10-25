package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
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
@DisplayName("StudyPlanAuditLabelProvider")
class StudyPlanAuditLabelProviderTest {

    @Mock
    private SpecialtyRepository specialtyRepository;

    private StudyPlanAuditLabelProvider provider;

    @BeforeEach
    void setUp() {
        provider = new StudyPlanAuditLabelProvider(specialtyRepository);
    }

    private static AuditedRecord record(String id, Integer planCode, Long specialtyId) {
        Map<String, Object> values = new HashMap<>();
        values.put("planCode", planCode);
        values.put("specialty", specialtyId);
        return new AuditedRecord(id, values);
    }

    private static Specialty specialty(Long id, String name) {
        return Specialty.builder().id(id).name(name).build();
    }

    @Test
    @DisplayName("entityType es StudyPlan")
    void entityType_isStudyPlan() {
        assertThat(provider.entityType()).isEqualTo(StudyPlan.class);
    }

    @Test
    @DisplayName("formato 'Plan {código}, {especialidad}'")
    void labels_planAndSpecialty() {
        when(specialtyRepository.findAllById(any())).thenReturn(List.of(specialty(3L, "Ingeniería en Sistemas")));

        assertThat(provider.labels(List.of(record("1", 2023, 3L))))
                .containsExactly(Map.entry("1", "Plan 2023, Ingeniería en Sistemas"));
    }

    @Test
    @DisplayName("una sola consulta de especialidades para todos los registros, con ids sin repetir")
    @SuppressWarnings("unchecked")
    void labels_severalRecords_oneBatchLookup() {
        when(specialtyRepository.findAllById(any())).thenReturn(
                List.of(specialty(3L, "Sistemas"), specialty(4L, "Civil")));

        Map<String, String> labels = provider.labels(List.of(
                record("1", 2023, 3L), record("2", 2008, 3L), record("3", 2012, 4L)));

        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(specialtyRepository, times(1)).findAllById(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(3L, 4L);
        assertThat(labels).containsOnly(
                Map.entry("1", "Plan 2023, Sistemas"),
                Map.entry("2", "Plan 2008, Sistemas"),
                Map.entry("3", "Plan 2012, Civil"));
    }

    @Test
    @DisplayName("si no se encuentra la especialidad muestra solo el código de plan")
    void labels_specialtyNotFound_showsOnlyPlanCode() {
        when(specialtyRepository.findAllById(any())).thenReturn(List.of());

        assertThat(provider.labels(List.of(record("1", 2023, 3L)))).containsExactly(Map.entry("1", "Plan 2023"));
    }

    @Test
    @DisplayName("sin especialidad en el estado no consulta el repositorio")
    void labels_noSpecialtyId_noLookup() {
        assertThat(provider.labels(List.of(record("1", 2023, null)))).containsExactly(Map.entry("1", "Plan 2023"));

        verifyNoInteractions(specialtyRepository);
    }

    @Test
    @DisplayName("sin código de plan el registro no tiene etiqueta")
    void labels_noPlanCode_hasNoLabel() {
        when(specialtyRepository.findAllById(any())).thenReturn(List.of(specialty(3L, "Sistemas")));

        assertThat(provider.labels(List.of(record("1", null, 3L)))).isEmpty();
    }
}
