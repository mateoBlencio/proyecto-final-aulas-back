package ar.edu.utn.frc.siga.space.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.space.dto.response.ClassroomListItemDto;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomPermission;
import ar.edu.utn.frc.siga.space.model.ClassroomResource;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.model.PermissionMode;
import ar.edu.utn.frc.siga.space.model.PermissionTargetKind;
import ar.edu.utn.frc.siga.space.model.ResourceType;
import ar.edu.utn.frc.siga.space.model.ResourceValueKind;
import ar.edu.utn.frc.siga.space.permission.PermissionTargetResolvers;
import ar.edu.utn.frc.siga.space.repository.ClassroomPermissionRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomResourceRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClassroomListComposer")
class ClassroomListComposerTest {

    @Mock
    private ClassroomResourceRepository classroomResourceRepository;
    @Mock
    private ClassroomPermissionRepository classroomPermissionRepository;
    @Mock
    private PermissionTargetResolvers permissionTargetResolvers;

    private ClassroomListComposer composer;

    @BeforeEach
    void setUp() {
        composer = new ClassroomListComposer(
                classroomResourceRepository, classroomPermissionRepository, permissionTargetResolvers);
    }

    private Classroom classroom(long id, PermissionMode mode) {
        return Classroom.builder()
                .id(id)
                .roomNumber(100 + (int) id)
                .capacity(40)
                .building(Building.builder().id(1L).name("Edificio Central").build())
                .classroomType(ClassroomType.builder().id(1L).description("Normal").build())
                .permissionMode(mode)
                .build();
    }

    private ClassroomResource resource(Classroom classroom, long typeId, String name, ResourceValueKind kind, int qty) {
        return ClassroomResource.builder()
                .classroom(classroom)
                .resourceType(ResourceType.builder().id(typeId).name(name).valueKind(kind).build())
                .quantity(qty)
                .build();
    }

    private ClassroomPermission permission(Classroom classroom, long targetId) {
        return ClassroomPermission.builder()
                .classroom(classroom)
                .targetKind(PermissionTargetKind.SUBJECT)
                .targetId(targetId)
                .build();
    }

    @Test
    @DisplayName("expone los recursos asociados como lista y allowedDisplay 'Todas' para modo ALL")
    void exposesResourceList() {
        Classroom classroom = classroom(1L, PermissionMode.ALL);
        when(classroomResourceRepository.findByClassroomIdInAndDeletedAtIsNull(anyCollection())).thenReturn(List.of(
                resource(classroom, 1L, "Cantidad de PC", ResourceValueKind.COUNT, 30),
                resource(classroom, 2L, "Proyector", ResourceValueKind.BOOLEAN, 1),
                resource(classroom, 3L, "Aire acondicionado", ResourceValueKind.BOOLEAN, 0)));
        when(classroomPermissionRepository.findByClassroomIdInAndDeletedAtIsNull(anyCollection())).thenReturn(List.of());

        ClassroomListItemDto dto = compose(classroom);

        assertThat(dto.resources()).hasSize(3)
                .anySatisfy(resource -> {
                    assertThat(resource.resourceTypeId()).isEqualTo(1L);
                    assertThat(resource.name()).isEqualTo("Cantidad de PC");
                    assertThat(resource.valueKind()).isEqualTo(ResourceValueKind.COUNT);
                    assertThat(resource.quantity()).isEqualTo(30);
                });
        assertThat(dto.allowedDisplay()).isEqualTo("Todas");
    }

    @Test
    @DisplayName("SUBSET con 1 entrada muestra el nombre resuelto")
    void subsetSingleShowsResolvedName() {
        Classroom classroom = classroom(1L, PermissionMode.SUBSET);
        when(classroomResourceRepository.findByClassroomIdInAndDeletedAtIsNull(anyCollection())).thenReturn(List.of());
        when(classroomPermissionRepository.findByClassroomIdInAndDeletedAtIsNull(anyCollection()))
                .thenReturn(List.of(permission(classroom, 7L)));
        when(permissionTargetResolvers.resolveNames(any(), anyCollection()))
                .thenReturn(Map.of(7L, "Análisis Matemático"));

        ClassroomListItemDto dto = compose(classroom);

        assertThat(dto.allowedDisplay()).isEqualTo("Análisis Matemático");
        assertThat(dto.permissionTargets()).singleElement()
                .satisfies(target -> assertThat(target.name()).isEqualTo("Análisis Matemático"));
    }

    @Test
    @DisplayName("SUBSET con más de 1 entrada muestra 'Algunos'")
    void subsetManyShowsSome() {
        Classroom classroom = classroom(1L, PermissionMode.SUBSET);
        when(classroomResourceRepository.findByClassroomIdInAndDeletedAtIsNull(anyCollection())).thenReturn(List.of());
        when(classroomPermissionRepository.findByClassroomIdInAndDeletedAtIsNull(anyCollection()))
                .thenReturn(List.of(permission(classroom, 7L), permission(classroom, 8L)));
        when(permissionTargetResolvers.resolveNames(any(), anyCollection()))
                .thenReturn(Map.of(7L, "A", 8L, "B"));

        assertThat(compose(classroom).allowedDisplay()).isEqualTo("Algunos");
    }

    @Test
    @DisplayName("NONE muestra 'Ninguna' y no consulta resolvers")
    void noneShowsNinguna() {
        Classroom classroom = classroom(1L, PermissionMode.NONE);
        when(classroomResourceRepository.findByClassroomIdInAndDeletedAtIsNull(anyCollection())).thenReturn(List.of());
        when(classroomPermissionRepository.findByClassroomIdInAndDeletedAtIsNull(anyCollection())).thenReturn(List.of());

        assertThat(compose(classroom).allowedDisplay()).isEqualTo("Ninguna");
    }

    @Test
    @DisplayName("aula desactivada: enabled=false")
    void deactivatedClassroomNotEnabled() {
        Classroom classroom = classroom(1L, PermissionMode.ALL);
        classroom.deactivate();
        when(classroomResourceRepository.findByClassroomIdInAndDeletedAtIsNull(anyCollection())).thenReturn(List.of());
        when(classroomPermissionRepository.findByClassroomIdInAndDeletedAtIsNull(anyCollection())).thenReturn(List.of());

        assertThat(compose(classroom).enabled()).isFalse();
    }

    @Test
    @DisplayName("página vacía: no consulta repositorios de recursos/permisos")
    void emptyPageSkipsQueries() {
        Page<Classroom> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        assertThat(composer.compose(empty).getContent()).isEmpty();
    }

    private ClassroomListItemDto compose(Classroom classroom) {
        Page<Classroom> page = new PageImpl<>(List.of(classroom), PageRequest.of(0, 20), 1);
        return composer.compose(page).getContent().getFirst();
    }
}
