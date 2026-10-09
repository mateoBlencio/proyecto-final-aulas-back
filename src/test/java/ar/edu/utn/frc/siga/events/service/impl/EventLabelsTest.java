package ar.edu.utn.frc.siga.events.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.events.service.impl.EventLabels.EventRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventLabels")
class EventLabelsTest {

    @Mock
    private SubjectService subjectService;
    @Mock
    private CommissionService commissionService;

    private EventLabels labels;

    @BeforeEach
    void setUp() {
        labels = new EventLabels(subjectService, commissionService);
    }

    private static SubjectResponseDto subject(Long id, String name) {
        return new SubjectResponseDto(id, 100, name, null, null, true);
    }

    private static CommissionResponseDto commission(Long id, String courseCode) {
        return new CommissionResponseDto(id, courseCode, null);
    }

    @Test
    @DisplayName("evento con materia y comisión: '{materia} {comisión}'")
    void labelsOf_subjectAndCommission() {
        when(subjectService.findByIdsIncludingDeactivated(any())).thenReturn(List.of(subject(1L, "Álgebra")));
        when(commissionService.findByIds(any())).thenReturn(List.of(commission(2L, "1K1")));

        Map<Long, String> result = labels.labelsOf(Map.of(10L, new EventRef(1L, 2L, null)));

        assertThat(result).containsExactly(Map.entry(10L, "Álgebra 1K1"));
    }

    @Test
    @DisplayName("una sola llamada en lote a materias (incluyendo desactivadas) y otra a comisiones, con ids sin repetir")
    @SuppressWarnings("unchecked")
    void labelsOf_manyEvents_oneBatchCallPerCatalog() {
        when(subjectService.findByIdsIncludingDeactivated(any())).thenReturn(List.of(subject(1L, "Álgebra"), subject(3L, "Física")));
        when(commissionService.findByIds(any())).thenReturn(List.of(commission(2L, "1K1"), commission(4L, "2K2")));
        Map<Long, EventRef> refs = new LinkedHashMap<>();
        refs.put(10L, new EventRef(1L, 2L, null));
        refs.put(11L, new EventRef(1L, 4L, null));
        refs.put(12L, new EventRef(3L, 2L, null));

        Map<Long, String> result = labels.labelsOf(refs);

        ArgumentCaptor<Collection<Long>> subjectIds = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Collection<Long>> commissionIds = ArgumentCaptor.forClass(Collection.class);
        verify(subjectService, times(1)).findByIdsIncludingDeactivated(subjectIds.capture());
        verify(commissionService, times(1)).findByIds(commissionIds.capture());
        verify(subjectService, never()).findByIds(any());
        assertThat(subjectIds.getValue()).containsExactlyInAnyOrder(1L, 3L);
        assertThat(commissionIds.getValue()).containsExactlyInAnyOrder(2L, 4L);
        assertThat(result).containsOnly(
                Map.entry(10L, "Álgebra 1K1"), Map.entry(11L, "Álgebra 2K2"), Map.entry(12L, "Física 1K1"));
    }

    @Test
    @DisplayName("con materia y sin comisión muestra solo la materia")
    void labelsOf_subjectWithoutCommission_showsSubject() {
        when(subjectService.findByIdsIncludingDeactivated(any())).thenReturn(List.of(subject(1L, "Álgebra")));

        assertThat(labels.labelsOf(Map.of(10L, new EventRef(1L, null, null))))
                .containsExactly(Map.entry(10L, "Álgebra"));
        verifyNoInteractions(commissionService);
    }

    @Test
    @DisplayName("evento único sin materia usa la descripción, sin la comisión")
    void labelsOf_noSubject_usesDescription() {
        when(commissionService.findByIds(any())).thenReturn(List.of(commission(2L, "1K1")));

        assertThat(labels.labelsOf(Map.of(10L, new EventRef(null, 2L, "Charla de ingreso"))))
                .containsExactly(Map.entry(10L, "Charla de ingreso"));
    }

    @Test
    @DisplayName("si la materia ya no se encuentra, cae a la descripción")
    void labelsOf_subjectNotFound_usesDescription() {
        when(subjectService.findByIdsIncludingDeactivated(any())).thenReturn(List.of());

        assertThat(labels.labelsOf(Map.of(10L, new EventRef(1L, null, "Charla de ingreso"))))
                .containsExactly(Map.entry(10L, "Charla de ingreso"));
    }

    @Test
    @DisplayName("sin materia y con descripción en blanco usa la comisión")
    void labelsOf_noSubjectBlankDescription_usesCommission() {
        when(commissionService.findByIds(any())).thenReturn(List.of(commission(2L, "1K1")));

        assertThat(labels.labelsOf(Map.of(10L, new EventRef(null, 2L, "   "))))
                .containsExactly(Map.entry(10L, "1K1"));
    }

    @Test
    @DisplayName("sin materia, sin descripción y sin comisión el evento no tiene etiqueta")
    void labelsOf_nothing_hasNoLabel() {
        assertThat(labels.labelsOf(Map.of(10L, new EventRef(null, null, null)))).isEmpty();
        verifyNoInteractions(subjectService, commissionService);
    }
}
