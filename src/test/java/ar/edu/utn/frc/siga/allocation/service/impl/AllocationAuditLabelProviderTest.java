package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AllocationAuditLabelProvider")
class AllocationAuditLabelProviderTest {

    @Mock
    private ClassroomService classroomService;
    @Mock
    private OccurrenceService occurrenceService;

    private AllocationAuditLabelProvider provider;

    @BeforeEach
    void setUp() {
        provider = new AllocationAuditLabelProvider(classroomService, occurrenceService);
    }

    private static AuditedRecord record(String id, Long classroomId, Long occurrenceId) {
        Map<String, Object> values = new HashMap<>();
        values.put("classroomId", classroomId);
        values.put("occurrenceId", occurrenceId);
        return new AuditedRecord(id, values);
    }

    private static ClassroomResponseDto classroom(Long id, int roomNumber, String building) {
        return new ClassroomResponseDto(id, roomNumber, 40, 1L, building, 1L, "Aula común");
    }

    @Test
    @DisplayName("entityType es Allocation")
    void entityType_isAllocation() {
        assertThat(provider.entityType()).isEqualTo(Allocation.class);
    }

    @Test
    @DisplayName("formato completo: 'Aula {nro}, {edificio} · dd/MM/yyyy'")
    void labels_fullRecord_usesRoomAndDate() {
        when(classroomService.findByIdsIncludingDeactivated(any())).thenReturn(List.of(classroom(5L, 101, "Central")));
        when(occurrenceService.findDatesByIds(any())).thenReturn(Map.of(8L, LocalDate.of(2026, 3, 4)));

        Map<String, String> labels = provider.labels(List.of(record("1", 5L, 8L)));

        assertThat(labels).containsExactly(Map.entry("1", "Aula 101, Central · 04/03/2026"));
    }

    @Test
    @DisplayName("una sola llamada en lote a cada servicio, con los ids sin repetir, usando el finder que incluye desactivadas")
    @SuppressWarnings("unchecked")
    void labels_severalRecords_oneBatchCallPerService() {
        when(classroomService.findByIdsIncludingDeactivated(any()))
                .thenReturn(List.of(classroom(5L, 101, "Central"), classroom(6L, 202, "Anexo")));
        when(occurrenceService.findDatesByIds(any()))
                .thenReturn(Map.of(8L, LocalDate.of(2026, 3, 4), 9L, LocalDate.of(2026, 12, 31)));

        Map<String, String> labels = provider.labels(List.of(
                record("1", 5L, 8L), record("2", 5L, 9L), record("3", 6L, 9L)));

        ArgumentCaptor<Collection<Long>> classroomIds = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Collection<Long>> occurrenceIds = ArgumentCaptor.forClass(Collection.class);
        verify(classroomService, times(1)).findByIdsIncludingDeactivated(classroomIds.capture());
        verify(occurrenceService, times(1)).findDatesByIds(occurrenceIds.capture());
        verify(classroomService, never()).findByIds(any());
        assertThat(classroomIds.getValue()).containsExactlyInAnyOrder(5L, 6L);
        assertThat(occurrenceIds.getValue()).containsExactlyInAnyOrder(8L, 9L);
        assertThat(labels).containsOnly(
                Map.entry("1", "Aula 101, Central · 04/03/2026"),
                Map.entry("2", "Aula 101, Central · 31/12/2026"),
                Map.entry("3", "Aula 202, Anexo · 31/12/2026"));
    }

    @Test
    @DisplayName("sin el aula muestra solo la fecha")
    void labels_missingClassroom_showsOnlyDate() {
        when(classroomService.findByIdsIncludingDeactivated(any())).thenReturn(List.of());
        when(occurrenceService.findDatesByIds(any())).thenReturn(Map.of(8L, LocalDate.of(2026, 3, 4)));

        assertThat(provider.labels(List.of(record("1", 5L, 8L)))).containsExactly(Map.entry("1", "04/03/2026"));
    }

    @Test
    @DisplayName("con la ocurrencia borrada muestra solo 'Aula {nro}, {edificio}'")
    void labels_missingOccurrence_showsOnlyRoom() {
        when(classroomService.findByIdsIncludingDeactivated(any())).thenReturn(List.of(classroom(5L, 101, "Central")));
        when(occurrenceService.findDatesByIds(any())).thenReturn(Map.of());

        assertThat(provider.labels(List.of(record("1", 5L, 8L)))).containsExactly(Map.entry("1", "Aula 101, Central"));
    }

    @Test
    @DisplayName("sin aula ni ocurrencia el registro no tiene etiqueta")
    void labels_neitherFound_hasNoLabel() {
        when(classroomService.findByIdsIncludingDeactivated(any())).thenReturn(List.of());
        when(occurrenceService.findDatesByIds(any())).thenReturn(Map.of());

        assertThat(provider.labels(List.of(record("1", 5L, 8L)))).isEmpty();
    }

    @Test
    @DisplayName("sin ids de aula ni de ocurrencia en el estado no consulta ningún servicio")
    void labels_noReferences_queriesNoService() {
        assertThat(provider.labels(List.of(record("1", null, null)))).isEmpty();

        verifyNoInteractions(classroomService, occurrenceService);
    }

    @Test
    @DisplayName("un registro sin aula no consulta aulas pero sí etiqueta con la fecha")
    void labels_noClassroomId_skipsClassroomLookup() {
        when(occurrenceService.findDatesByIds(any())).thenReturn(Map.of(8L, LocalDate.of(2026, 3, 4)));

        assertThat(provider.labels(List.of(record("1", null, 8L)))).containsExactly(Map.entry("1", "04/03/2026"));

        verifyNoInteractions(classroomService);
    }
}
