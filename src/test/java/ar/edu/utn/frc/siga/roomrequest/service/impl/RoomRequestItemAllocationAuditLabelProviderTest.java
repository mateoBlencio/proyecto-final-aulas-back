package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceAuditRefDto;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestItemAllocationAuditLabelProvider")
class RoomRequestItemAllocationAuditLabelProviderTest {

    @Mock
    private ClassroomService classroomService;
    @Mock
    private OccurrenceService occurrenceService;

    private RoomRequestItemAllocationAuditLabelProvider provider;

    @BeforeEach
    void setUp() {
        provider = new RoomRequestItemAllocationAuditLabelProvider(classroomService, occurrenceService);
    }

    private static AuditedRecord record(String id, Long classroomId, Long occurrenceId) {
        Map<String, Object> values = new HashMap<>();
        values.put("classroomId", classroomId);
        values.put("occurrenceId", occurrenceId);
        return new AuditedRecord(id, values);
    }

    private static ClassroomResponseDto classroom() {
        return new ClassroomResponseDto(5L, 101, 40, 1L, "Central", 1L, "Aula común", null, true);
    }

    private static OccurrenceAuditRefDto ref(String eventLabel) {
        return new OccurrenceAuditRefDto(8L, 80L, eventLabel, LocalDate.of(2026, 3, 4));
    }

    @Test
    @DisplayName("entityType es RoomRequestItemAllocation")
    void entityType_isRoomRequestItemAllocation() {
        assertThat(provider.entityType()).isEqualTo(RoomRequestItemAllocation.class);
    }

    @Test
    @DisplayName("formato '{materia} ({comisión}) · Aula {nro}, {edificio} · dd/MM/yyyy'")
    void labels_fullRecord() {
        when(classroomService.findByIdsIncludingDeactivated(any())).thenReturn(List.of(classroom()));
        when(occurrenceService.findAuditRefs(any())).thenReturn(Map.of(8L, ref("Análisis Matemático I (1K1)")));

        assertThat(provider.labels(List.of(record("1", 5L, 8L))))
                .containsExactly(Map.entry("1", "Análisis Matemático I (1K1) · Aula 101, Central · 04/03/2026"));
    }

    @Test
    @DisplayName("con la ocurrencia borrada muestra solo el aula")
    void labels_missingOccurrence_showsOnlyRoom() {
        when(classroomService.findByIdsIncludingDeactivated(any())).thenReturn(List.of(classroom()));
        when(occurrenceService.findAuditRefs(any())).thenReturn(Map.of());

        assertThat(provider.labels(List.of(record("1", 5L, 8L))))
                .containsExactly(Map.entry("1", "Aula 101, Central"));
    }

    @Test
    @DisplayName("summaryLabels es solo materia y comisión y no consulta aulas")
    void summaryLabels_onlyEventLabel() {
        when(occurrenceService.findAuditRefs(any())).thenReturn(Map.of(8L, ref("Física I (2K3)")));

        assertThat(provider.summaryLabels(List.of(record("1", 5L, 8L))))
                .containsExactly(Map.entry("1", "Física I (2K3)"));
        verifyNoInteractions(classroomService);
    }

    @Test
    @DisplayName("sin ids de aula ni de ocurrencia no consulta ningún servicio y no hay etiqueta")
    void labels_noReferences_queriesNothing() {
        assertThat(provider.labels(List.of(record("1", null, null)))).isEmpty();

        verifyNoInteractions(classroomService, occurrenceService);
    }
}
