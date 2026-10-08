package ar.edu.utn.frc.siga.events.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.model.UniqueEvent;
import ar.edu.utn.frc.siga.events.repository.AcademicEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
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
@DisplayName("OccurrenceAuditLabelProvider")
class OccurrenceAuditLabelProviderTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 4);

    @Mock
    private AcademicEventRepository eventRepository;
    @Mock
    private SubjectService subjectService;
    @Mock
    private CommissionService commissionService;

    private OccurrenceAuditLabelProvider provider;

    @BeforeEach
    void setUp() {
        provider = new OccurrenceAuditLabelProvider(eventRepository, new EventLabels(subjectService, commissionService));
    }

    private static AuditedRecord record(String id, Long eventId, LocalDate date) {
        Map<String, Object> values = new HashMap<>();
        values.put("event", eventId);
        values.put("date", date);
        return new AuditedRecord(id, values);
    }

    private static RecurringEvent recurring(Long id, Long subjectId, Long commissionId) {
        return RecurringEvent.builder().id(id).subjectId(subjectId).commissionId(commissionId).build();
    }

    private static UniqueEvent unique(Long id, Long subjectId, Long commissionId, String description) {
        return UniqueEvent.builder().id(id).subjectId(subjectId).commissionId(commissionId).description(description).build();
    }

    private void catalogs() {
        when(subjectService.findByIdsIncludingDeactivated(any()))
                .thenReturn(List.of(new SubjectResponseDto(1L, 100, "Álgebra", null, null, true)));
        when(commissionService.findByIds(any())).thenReturn(List.of(new CommissionResponseDto(2L, "1K1", null)));
    }

    @Test
    @DisplayName("entityType es Occurrence")
    void entityType_isOccurrence() {
        assertThat(provider.entityType()).isEqualTo(Occurrence.class);
    }

    @Test
    @DisplayName("formato '{materia} {comisión} · fecha'")
    void labels_eventAndDate() {
        catalogs();
        when(eventRepository.findAllById(any())).thenReturn(List.of(recurring(10L, 1L, 2L)));

        Map<String, String> labels = provider.labels(List.of(record("100", 10L, DATE)));

        assertThat(labels).containsExactly(Map.entry("100", "Álgebra 1K1 · 04/03/2026"));
    }

    @Test
    @DisplayName("una sola llamada en lote por catálogo (eventos, materias, comisiones) para varias ocurrencias del mismo evento")
    @SuppressWarnings("unchecked")
    void labels_severalOccurrences_oneBatchCallPerCatalog() {
        catalogs();
        when(eventRepository.findAllById(any())).thenReturn(List.of(recurring(10L, 1L, 2L)));

        Map<String, String> labels = provider.labels(List.of(
                record("100", 10L, DATE), record("101", 10L, DATE.plusDays(7))));

        ArgumentCaptor<Collection<Long>> eventIds = ArgumentCaptor.forClass(Collection.class);
        verify(eventRepository, times(1)).findAllById(eventIds.capture());
        verify(subjectService, times(1)).findByIdsIncludingDeactivated(any());
        verify(commissionService, times(1)).findByIds(any());
        assertThat(eventIds.getValue()).containsExactly(10L);
        assertThat(labels).containsOnly(
                Map.entry("100", "Álgebra 1K1 · 04/03/2026"), Map.entry("101", "Álgebra 1K1 · 11/03/2026"));
    }

    @Test
    @DisplayName("si el evento no existe más muestra solo la fecha")
    void labels_eventGone_showsOnlyDate() {
        when(eventRepository.findAllById(any())).thenReturn(List.of());

        assertThat(provider.labels(List.of(record("100", 10L, DATE))))
                .containsExactly(Map.entry("100", "04/03/2026"));
    }

    @Test
    @DisplayName("sin fecha en el estado muestra solo la etiqueta del evento")
    void labels_noDate_showsOnlyEvent() {
        catalogs();
        when(eventRepository.findAllById(any())).thenReturn(List.of(recurring(10L, 1L, 2L)));

        assertThat(provider.labels(List.of(record("100", 10L, null))))
                .containsExactly(Map.entry("100", "Álgebra 1K1"));
    }

    @Test
    @DisplayName("evento único sin materia: usa su descripción junto con la fecha")
    void labels_uniqueEventWithoutSubject_usesDescription() {
        when(eventRepository.findAllById(any())).thenReturn(List.of(unique(10L, null, null, "Charla de ingreso")));

        assertThat(provider.labels(List.of(record("100", 10L, DATE))))
                .containsExactly(Map.entry("100", "Charla de ingreso · 04/03/2026"));
        verifyNoInteractions(subjectService, commissionService);
    }

    @Test
    @DisplayName("sin evento ni fecha en el estado no hay etiqueta")
    void labels_noEventNoDate_hasNoLabel() {
        when(eventRepository.findAllById(any())).thenReturn(List.of());

        assertThat(provider.labels(List.of(record("100", null, null)))).isEmpty();
    }
}
