package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.model.ClassroomPermission;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClassroomPermissionAuditLabelProvider")
class ClassroomPermissionAuditLabelProviderTest {

    @Mock
    private SubjectService subjectService;
    @Mock
    private ClassroomService classroomService;

    private ClassroomPermissionAuditLabelProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ClassroomPermissionAuditLabelProvider(subjectService, classroomService);
    }

    private static AuditedRecord record(String id, String targetKind, Long targetId, Long classroomId) {
        Map<String, Object> values = new HashMap<>();
        values.put("targetKind", targetKind);
        values.put("targetId", targetId);
        values.put("classroom", classroomId);
        return new AuditedRecord(id, values);
    }

    private static SubjectResponseDto subject(Long id, String name) {
        return new SubjectResponseDto(id, 1, name, null, null, true, null);
    }

    private static ClassroomResponseDto classroom(Long id, int roomNumber, String buildingName) {
        return new ClassroomResponseDto(id, roomNumber, 30, 1L, buildingName, 1L, "Aula", null, true);
    }

    @Test
    @DisplayName("entityType es ClassroomPermission")
    void entityType_isClassroomPermission() {
        assertThat(provider.entityType()).isEqualTo(ClassroomPermission.class);
    }

    @Test
    @DisplayName("formato 'Materia {nombre} en Aula {número}, {edificio}'")
    void labels_subjectAndClassroom() {
        when(subjectService.findByIdsIncludingDeactivated(any())).thenReturn(List.of(subject(7L, "Física I")));
        when(classroomService.findByIdsIncludingDeactivated(any())).thenReturn(List.of(classroom(9L, 101, "Central")));

        assertThat(provider.labels(List.of(record("1", "SUBJECT", 7L, 9L))))
                .containsExactly(Map.entry("1", "Materia Física I en Aula 101, Central"));
    }

    @Test
    @DisplayName("una sola consulta de materias y una de aulas para todos los registros, con ids sin repetir")
    @SuppressWarnings("unchecked")
    void labels_severalRecords_oneBatchLookupPerCatalog() {
        when(subjectService.findByIdsIncludingDeactivated(any())).thenReturn(
                List.of(subject(7L, "Física I"), subject(8L, "Álgebra")));
        when(classroomService.findByIdsIncludingDeactivated(any())).thenReturn(
                List.of(classroom(9L, 101, "Central"), classroom(10L, 5, "Anexo")));

        Map<String, String> labels = provider.labels(List.of(
                record("1", "SUBJECT", 7L, 9L), record("2", "SUBJECT", 8L, 9L), record("3", "SUBJECT", 7L, 10L)));

        ArgumentCaptor<Collection<Long>> subjectIds = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Collection<Long>> classroomIds = ArgumentCaptor.forClass(Collection.class);
        verify(subjectService, times(1)).findByIdsIncludingDeactivated(subjectIds.capture());
        verify(classroomService, times(1)).findByIdsIncludingDeactivated(classroomIds.capture());
        assertThat(subjectIds.getValue()).containsExactlyInAnyOrder(7L, 8L);
        assertThat(classroomIds.getValue()).containsExactlyInAnyOrder(9L, 10L);
        assertThat(labels).containsOnly(
                Map.entry("1", "Materia Física I en Aula 101, Central"),
                Map.entry("2", "Materia Álgebra en Aula 101, Central"),
                Map.entry("3", "Materia Física I en Aula 5, Anexo"));
    }

    @Test
    @DisplayName("si no se encuentra la materia muestra solo el aula")
    void labels_subjectNotFound_showsOnlyClassroom() {
        when(subjectService.findByIdsIncludingDeactivated(any())).thenReturn(List.of());
        when(classroomService.findByIdsIncludingDeactivated(any())).thenReturn(List.of(classroom(9L, 101, "Central")));

        assertThat(provider.labels(List.of(record("1", "SUBJECT", 7L, 9L))))
                .containsExactly(Map.entry("1", "Aula 101, Central"));
    }

    @Test
    @DisplayName("si no se encuentra el aula muestra solo la materia")
    void labels_classroomNotFound_showsOnlySubject() {
        when(subjectService.findByIdsIncludingDeactivated(any())).thenReturn(List.of(subject(7L, "Física I")));
        when(classroomService.findByIdsIncludingDeactivated(any())).thenReturn(List.of());

        assertThat(provider.labels(List.of(record("1", "SUBJECT", 7L, 9L))))
                .containsExactly(Map.entry("1", "Materia Física I"));
    }

    @Test
    @DisplayName("un objetivo que no es materia no consulta materias y muestra solo el aula")
    void labels_nonSubjectTarget_doesNotLookUpSubjects() {
        when(classroomService.findByIdsIncludingDeactivated(any())).thenReturn(List.of(classroom(9L, 101, "Central")));

        assertThat(provider.labels(List.of(record("1", "OTHER", 7L, 9L))))
                .containsExactly(Map.entry("1", "Aula 101, Central"));

        verify(subjectService, never()).findByIdsIncludingDeactivated(any());
    }

    @Test
    @DisplayName("si no se encuentra ninguna referencia el registro no tiene etiqueta")
    void labels_nothingFound_hasNoLabel() {
        when(subjectService.findByIdsIncludingDeactivated(any())).thenReturn(List.of());
        when(classroomService.findByIdsIncludingDeactivated(any())).thenReturn(List.of());

        assertThat(provider.labels(List.of(record("1", "SUBJECT", 7L, 9L)))).isEmpty();
    }
}
