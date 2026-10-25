package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
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
@DisplayName("ClassroomAuditLabelProvider")
class ClassroomAuditLabelProviderTest {

    @Mock
    private BuildingRepository buildingRepository;

    private ClassroomAuditLabelProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ClassroomAuditLabelProvider(buildingRepository);
    }

    private static AuditedRecord record(String id, Integer roomNumber, Long buildingId) {
        Map<String, Object> values = new HashMap<>();
        values.put("roomNumber", roomNumber);
        values.put("building", buildingId);
        return new AuditedRecord(id, values);
    }

    private static Building building(Long id, String name) {
        return Building.builder().id(id).name(name).build();
    }

    @Test
    @DisplayName("entityType es Classroom")
    void entityType_isClassroom() {
        assertThat(provider.entityType()).isEqualTo(Classroom.class);
    }

    @Test
    @DisplayName("formato 'Aula {número}, {edificio}'")
    void labels_roomAndBuilding() {
        when(buildingRepository.findAllById(any())).thenReturn(List.of(building(2L, "Central")));

        assertThat(provider.labels(List.of(record("1", 101, 2L))))
                .containsExactly(Map.entry("1", "Aula 101, Central"));
    }

    @Test
    @DisplayName("una sola consulta de edificios para todos los registros, con ids sin repetir")
    @SuppressWarnings("unchecked")
    void labels_severalRecords_oneBatchLookup() {
        when(buildingRepository.findAllById(any())).thenReturn(
                List.of(building(2L, "Central"), building(3L, "Anexo")));

        Map<String, String> labels = provider.labels(List.of(
                record("1", 101, 2L), record("2", 102, 2L), record("3", 5, 3L)));

        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(buildingRepository, times(1)).findAllById(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(2L, 3L);
        assertThat(labels).containsOnly(
                Map.entry("1", "Aula 101, Central"),
                Map.entry("2", "Aula 102, Central"),
                Map.entry("3", "Aula 5, Anexo"));
    }

    @Test
    @DisplayName("si no se encuentra el edificio muestra solo el número de aula")
    void labels_buildingNotFound_showsOnlyRoomNumber() {
        when(buildingRepository.findAllById(any())).thenReturn(List.of());

        assertThat(provider.labels(List.of(record("1", 101, 2L)))).containsExactly(Map.entry("1", "Aula 101"));
    }

    @Test
    @DisplayName("sin edificio en el estado no consulta el repositorio")
    void labels_noBuildingId_noLookup() {
        assertThat(provider.labels(List.of(record("1", 101, null)))).containsExactly(Map.entry("1", "Aula 101"));

        verifyNoInteractions(buildingRepository);
    }

    @Test
    @DisplayName("sin número de aula el registro no tiene etiqueta")
    void labels_noRoomNumber_hasNoLabel() {
        when(buildingRepository.findAllById(any())).thenReturn(List.of(building(2L, "Central")));

        assertThat(provider.labels(List.of(record("1", null, 2L)))).isEmpty();
    }
}
