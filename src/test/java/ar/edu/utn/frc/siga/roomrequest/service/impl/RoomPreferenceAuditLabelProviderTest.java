package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.roomrequest.model.RoomPreference;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomPreferenceAuditLabelProvider")
class RoomPreferenceAuditLabelProviderTest {

    @Mock
    private ClassroomService classroomService;

    private RoomPreferenceAuditLabelProvider provider;

    @BeforeEach
    void setUp() {
        provider = new RoomPreferenceAuditLabelProvider(classroomService);
    }

    private static AuditedRecord record(String id, Long classroomId) {
        Map<String, Object> values = new HashMap<>();
        values.put("classroomId", classroomId);
        return new AuditedRecord(id, values);
    }

    private static ClassroomResponseDto classroom(Long id, int roomNumber, String building) {
        return new ClassroomResponseDto(id, roomNumber, 40, 1L, building, 1L, "Aula común", null, true);
    }

    @Test
    @DisplayName("entityType es RoomPreference")
    void entityType_isRoomPreference() {
        assertThat(provider.entityType()).isEqualTo(RoomPreference.class);
    }

    @Test
    @DisplayName("formato 'Aula {nro}, {edificio}' con una sola consulta en lote que incluye desactivadas")
    void labels_roomAndBuilding_oneBatchCall() {
        when(classroomService.findByIdsIncludingDeactivated(any()))
                .thenReturn(List.of(classroom(5L, 101, "Central"), classroom(6L, 202, "Anexo")));

        Map<String, String> labels = provider.labels(List.of(record("1", 5L), record("2", 6L), record("3", 5L)));

        assertThat(labels).containsOnly(Map.entry("1", "Aula 101, Central"), Map.entry("2", "Aula 202, Anexo"),
                Map.entry("3", "Aula 101, Central"));
        verify(classroomService, times(1)).findByIdsIncludingDeactivated(any());
    }

    @Test
    @DisplayName("si el aula no se encuentra o no hay id, el registro no tiene etiqueta")
    void labels_classroomMissing_hasNoLabel() {
        when(classroomService.findByIdsIncludingDeactivated(any())).thenReturn(List.of());

        assertThat(provider.labels(List.of(record("1", 5L), record("2", null)))).isEmpty();
    }

    @Test
    @DisplayName("sin ningún id de aula no consulta el servicio")
    void labels_noClassroomIds_queriesNothing() {
        assertThat(provider.labels(List.of(record("1", null)))).isEmpty();

        verifyNoInteractions(classroomService);
    }
}
