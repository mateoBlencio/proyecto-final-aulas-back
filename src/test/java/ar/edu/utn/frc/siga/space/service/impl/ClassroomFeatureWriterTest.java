package ar.edu.utn.frc.siga.space.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.space.dto.request.ClassroomPermissionTargetRequestDto;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomResourceRequestDto;
import ar.edu.utn.frc.siga.space.exception.SpaceDomainException;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomPermission;
import ar.edu.utn.frc.siga.space.model.ClassroomResource;
import ar.edu.utn.frc.siga.space.model.PermissionMode;
import ar.edu.utn.frc.siga.space.model.PermissionTargetKind;
import ar.edu.utn.frc.siga.space.model.ResourceType;
import ar.edu.utn.frc.siga.space.model.ResourceValueKind;
import ar.edu.utn.frc.siga.space.repository.ClassroomPermissionRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomResourceRepository;
import ar.edu.utn.frc.siga.space.repository.ResourceTypeRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClassroomFeatureWriter")
class ClassroomFeatureWriterTest {

    @Mock
    private ResourceTypeRepository resourceTypeRepository;
    @Mock
    private ClassroomResourceRepository classroomResourceRepository;
    @Mock
    private ClassroomPermissionRepository classroomPermissionRepository;

    private ClassroomFeatureWriter writer;
    private Classroom classroom;

    @BeforeEach
    void setUp() {
        writer = new ClassroomFeatureWriter(
                resourceTypeRepository, classroomResourceRepository, classroomPermissionRepository);
        classroom = Classroom.builder().id(1L).roomNumber(101).capacity(40).build();
    }

    private ResourceType type(long id, String name) {
        return ResourceType.builder().id(id).name(name).valueKind(ResourceValueKind.COUNT).build();
    }

    @Test
    @DisplayName("applyResources: alta, actualización de cantidad y baja (soft-delete) según el pedido")
    void applyResourcesReconciles() {
        ClassroomResource keepPc = ClassroomResource.builder()
                .classroom(classroom).resourceType(type(1L, "Cantidad de PC")).quantity(10).build();
        ClassroomResource dropProjector = ClassroomResource.builder()
                .classroom(classroom).resourceType(type(2L, "Proyector")).quantity(1).build();
        when(classroomResourceRepository.findByClassroomId(1L)).thenReturn(List.of(keepPc, dropProjector));
        when(resourceTypeRepository.findActiveById(3L))
                .thenReturn(Optional.of(type(3L, "Aire acondicionado")));

        writer.applyResources(classroom, List.of(
                new ClassroomResourceRequestDto(1L, 25),
                new ClassroomResourceRequestDto(3L, 1)));

        assertThat(keepPc.getQuantity()).isEqualTo(25);
        verify(classroomResourceRepository).softDelete(dropProjector);
        verify(classroomResourceRepository).save(keepPc);
    }

    @Test
    @DisplayName("applyResources: id de tipo de recurso desconocido lanza SpaceDomainException")
    void applyResourcesUnknownResourceTypeIdThrows() {
        when(classroomResourceRepository.findByClassroomId(1L)).thenReturn(List.of());
        when(resourceTypeRepository.findActiveById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> writer.applyResources(classroom,
                List.of(new ClassroomResourceRequestDto(999L, 1))))
                .isInstanceOf(SpaceDomainException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("applyPermissions: modo != SUBSET da de baja todas las entradas activas")
    void applyPermissionsNonSubsetClearsAll() {
        ClassroomPermission entry = ClassroomPermission.builder()
                .classroom(classroom).targetKind(PermissionTargetKind.SUBJECT).targetId(7L).build();
        when(classroomPermissionRepository.findByClassroomId(1L)).thenReturn(List.of(entry));

        writer.applyPermissions(classroom, PermissionMode.ALL, List.of(
                new ClassroomPermissionTargetRequestDto(PermissionTargetKind.SUBJECT, 7L)));

        verify(classroomPermissionRepository).softDelete(entry);
        verify(classroomPermissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("applyPermissions: SUBSET agrega las nuevas, restaura las soft-deleted y da de baja las sobrantes")
    void applyPermissionsSubsetReconciles() {
        ClassroomPermission keep = ClassroomPermission.builder()
                .classroom(classroom).targetKind(PermissionTargetKind.SUBJECT).targetId(7L).build();
        ClassroomPermission drop = ClassroomPermission.builder()
                .classroom(classroom).targetKind(PermissionTargetKind.SUBJECT).targetId(8L).build();
        ClassroomPermission restore = ClassroomPermission.builder()
                .classroom(classroom).targetKind(PermissionTargetKind.SUBJECT).targetId(9L).build();
        restore.deactivate();
        when(classroomPermissionRepository.findByClassroomId(1L)).thenReturn(List.of(keep, drop, restore));

        writer.applyPermissions(classroom, PermissionMode.SUBSET, List.of(
                new ClassroomPermissionTargetRequestDto(PermissionTargetKind.SUBJECT, 7L),
                new ClassroomPermissionTargetRequestDto(PermissionTargetKind.SUBJECT, 9L)));

        verify(classroomPermissionRepository).restore(restore);
        verify(classroomPermissionRepository).softDelete(drop);
        verify(classroomPermissionRepository, never()).save(any());
    }
}
