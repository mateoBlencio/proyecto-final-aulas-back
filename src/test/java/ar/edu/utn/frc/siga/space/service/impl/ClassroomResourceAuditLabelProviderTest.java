package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.model.ClassroomResource;
import ar.edu.utn.frc.siga.space.model.ResourceType;
import ar.edu.utn.frc.siga.space.repository.ResourceTypeRepository;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
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
@DisplayName("ClassroomResourceAuditLabelProvider")
class ClassroomResourceAuditLabelProviderTest {

    @Mock
    private ResourceTypeRepository resourceTypeRepository;
    @Mock
    private ClassroomService classroomService;

    private ClassroomResourceAuditLabelProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ClassroomResourceAuditLabelProvider(resourceTypeRepository, classroomService);
    }

    private static AuditedRecord record(String id, Long resourceTypeId, Long classroomId) {
        Map<String, Object> values = new HashMap<>();
        values.put("resourceType", resourceTypeId);
        values.put("classroom", classroomId);
        return new AuditedRecord(id, values);
    }

    private static ResourceType type(Long id, String name) {
        return ResourceType.builder().id(id).name(name).build();
    }

    private static ClassroomResponseDto classroom(Long id, int roomNumber, String buildingName) {
        return new ClassroomResponseDto(id, roomNumber, 30, 1L, buildingName, 1L, "Aula", null, true);
    }

    @Test
    @DisplayName("entityType es ClassroomResource")
    void entityType_isClassroomResource() {
        assertThat(provider.entityType()).isEqualTo(ClassroomResource.class);
    }

    @Test
    @DisplayName("formato '{tipo de recurso} en Aula {número}, {edificio}'")
    void labels_typeAndClassroom() {
        when(resourceTypeRepository.findAllById(any())).thenReturn(List.of(type(4L, "Proyector")));
        when(classroomService.findByIdsIncludingDeactivated(any())).thenReturn(List.of(classroom(9L, 101, "Central")));

        assertThat(provider.labels(List.of(record("1", 4L, 9L))))
                .containsExactly(Map.entry("1", "Proyector en Aula 101, Central"));
    }

    @Test
    @DisplayName("una sola consulta de tipos y una de aulas para todos los registros, con ids sin repetir")
    @SuppressWarnings("unchecked")
    void labels_severalRecords_oneBatchLookupPerCatalog() {
        when(resourceTypeRepository.findAllById(any())).thenReturn(List.of(type(4L, "Proyector"), type(5L, "Pizarra")));
        when(classroomService.findByIdsIncludingDeactivated(any())).thenReturn(
                List.of(classroom(9L, 101, "Central"), classroom(10L, 5, "Anexo")));

        Map<String, String> labels = provider.labels(List.of(
                record("1", 4L, 9L), record("2", 5L, 9L), record("3", 4L, 10L)));

        ArgumentCaptor<Collection<Long>> typeIds = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Collection<Long>> classroomIds = ArgumentCaptor.forClass(Collection.class);
        verify(resourceTypeRepository, times(1)).findAllById(typeIds.capture());
        verify(classroomService, times(1)).findByIdsIncludingDeactivated(classroomIds.capture());
        assertThat(typeIds.getValue()).containsExactlyInAnyOrder(4L, 5L);
        assertThat(classroomIds.getValue()).containsExactlyInAnyOrder(9L, 10L);
        assertThat(labels).containsOnly(
                Map.entry("1", "Proyector en Aula 101, Central"),
                Map.entry("2", "Pizarra en Aula 101, Central"),
                Map.entry("3", "Proyector en Aula 5, Anexo"));
    }

    @Test
    @DisplayName("si no se encuentra el aula muestra solo el tipo de recurso")
    void labels_classroomNotFound_showsOnlyType() {
        when(resourceTypeRepository.findAllById(any())).thenReturn(List.of(type(4L, "Proyector")));
        when(classroomService.findByIdsIncludingDeactivated(any())).thenReturn(List.of());

        assertThat(provider.labels(List.of(record("1", 4L, 9L)))).containsExactly(Map.entry("1", "Proyector"));
    }

    @Test
    @DisplayName("si no se encuentra el tipo muestra solo el aula")
    void labels_typeNotFound_showsOnlyClassroom() {
        when(resourceTypeRepository.findAllById(any())).thenReturn(List.of());
        when(classroomService.findByIdsIncludingDeactivated(any())).thenReturn(List.of(classroom(9L, 101, "Central")));

        assertThat(provider.labels(List.of(record("1", 4L, 9L))))
                .containsExactly(Map.entry("1", "Aula 101, Central"));
    }

    @Test
    @DisplayName("si no se encuentra ninguna referencia el registro no tiene etiqueta")
    void labels_nothingFound_hasNoLabel() {
        when(resourceTypeRepository.findAllById(any())).thenReturn(List.of());
        when(classroomService.findByIdsIncludingDeactivated(any())).thenReturn(List.of());

        assertThat(provider.labels(List.of(record("1", 4L, 9L)))).isEmpty();
    }

    @Test
    @DisplayName("sin referencias en el estado no consulta ningún catálogo")
    void labels_noReferences_noLookup() {
        assertThat(provider.labels(List.of(record("1", null, null)))).isEmpty();

        verifyNoInteractions(resourceTypeRepository, classroomService);
    }
}
