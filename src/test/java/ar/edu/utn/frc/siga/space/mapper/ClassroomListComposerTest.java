package ar.edu.utn.frc.siga.space.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
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
import ar.edu.utn.frc.siga.space.permission.TargetInfo;
import ar.edu.utn.frc.siga.space.repository.ClassroomPermissionRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomResourceRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    @Mock
    private BuildingScopeResolver scopeResolver;

    private ClassroomListComposer composer;

    @BeforeEach
    void setUp() {
        lenient().when(scopeResolver.scopeFor(any(Permission.class))).thenReturn(BuildingScope.denied());
        composer = new ClassroomListComposer(
                classroomResourceRepository, classroomPermissionRepository, permissionTargetResolvers,
                new SpaceActionFlags(scopeResolver));
    }

    private Classroom classroom(long id, PermissionMode mode) {
        return classroom(id, 1L, mode);
    }

    private Classroom classroom(long id, long buildingId, PermissionMode mode) {
        return Classroom.builder()
                .id(id)
                .roomNumber(100 + (int) id)
                .capacity(40)
                .building(Building.builder().id(buildingId).name("Edificio Central").build())
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
        when(permissionTargetResolvers.resolve(any(), anyCollection()))
                .thenReturn(Map.of(7L, TargetInfo.ofName("Análisis Matemático")));

        ClassroomListItemDto dto = compose(classroom);

        assertThat(dto.allowedDisplay()).isEqualTo("Análisis Matemático");
        assertThat(dto.permissionTargets()).singleElement()
                .satisfies(target -> assertThat(target.name()).isEqualTo("Análisis Matemático"));
    }

    @Test
    @DisplayName("el target lleva nombre, especialidad (nombre, abreviatura, código) y código de plan")
    void targetCarriesSpecialtyFields() {
        Classroom classroom = classroom(1L, PermissionMode.SUBSET);
        when(classroomResourceRepository.findByClassroomIdInAndDeletedAtIsNull(anyCollection())).thenReturn(List.of());
        when(classroomPermissionRepository.findByClassroomIdInAndDeletedAtIsNull(anyCollection()))
                .thenReturn(List.of(permission(classroom, 7L)));
        when(permissionTargetResolvers.resolve(any(), anyCollection()))
                .thenReturn(Map.of(7L, new TargetInfo("Física I", "Ingeniería en Sistemas", "ISI", 5, 2023)));

        assertThat(compose(classroom).permissionTargets()).singleElement().satisfies(target -> {
            assertThat(target.targetKind()).isEqualTo(PermissionTargetKind.SUBJECT);
            assertThat(target.targetId()).isEqualTo(7L);
            assertThat(target.name()).isEqualTo("Física I");
            assertThat(target.specialtyName()).isEqualTo("Ingeniería en Sistemas");
            assertThat(target.specialtyAbbreviation()).isEqualTo("ISI");
            assertThat(target.specialtyCode()).isEqualTo(5);
            assertThat(target.studyPlanCode()).isEqualTo(2023);
        });
    }

    @Test
    @DisplayName("target sin resolver: nombre y los cuatro campos de especialidad quedan null")
    void unresolvedTargetHasNullFields() {
        Classroom classroom = classroom(1L, PermissionMode.SUBSET);
        when(classroomResourceRepository.findByClassroomIdInAndDeletedAtIsNull(anyCollection())).thenReturn(List.of());
        when(classroomPermissionRepository.findByClassroomIdInAndDeletedAtIsNull(anyCollection()))
                .thenReturn(List.of(permission(classroom, 7L)));
        when(permissionTargetResolvers.resolve(any(), anyCollection())).thenReturn(Map.of());

        ClassroomListItemDto dto = compose(classroom);

        assertThat(dto.permissionTargets()).singleElement().satisfies(target -> {
            assertThat(target.targetId()).isEqualTo(7L);
            assertThat(target.name()).isNull();
            assertThat(target.specialtyName()).isNull();
            assertThat(target.specialtyAbbreviation()).isNull();
            assertThat(target.specialtyCode()).isNull();
            assertThat(target.studyPlanCode()).isNull();
        });
        assertThat(dto.allowedDisplay()).isEqualTo("Algunos");
    }

    @Test
    @DisplayName("SUBSET con más de 1 entrada muestra 'Algunos'")
    void subsetManyShowsSome() {
        Classroom classroom = classroom(1L, PermissionMode.SUBSET);
        when(classroomResourceRepository.findByClassroomIdInAndDeletedAtIsNull(anyCollection())).thenReturn(List.of());
        when(classroomPermissionRepository.findByClassroomIdInAndDeletedAtIsNull(anyCollection()))
                .thenReturn(List.of(permission(classroom, 7L), permission(classroom, 8L)));
        when(permissionTargetResolvers.resolve(any(), anyCollection()))
                .thenReturn(Map.of(7L, TargetInfo.ofName("A"), 8L, TargetInfo.ofName("B")));

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

    @Test
    @DisplayName("flags por aula: alcance de CLASSROOM_UPDATE en {1} marca la del edificio 1 y no la del 2; el alcance se resuelve una vez por página")
    void flagsFollowUserScopePerBuilding() {
        Classroom inScope = classroom(1L, 1L, PermissionMode.ALL);
        Classroom outOfScope = classroom(2L, 2L, PermissionMode.ALL);
        when(classroomResourceRepository.findByClassroomIdInAndDeletedAtIsNull(anyCollection())).thenReturn(List.of());
        when(classroomPermissionRepository.findByClassroomIdInAndDeletedAtIsNull(anyCollection())).thenReturn(List.of());
        when(scopeResolver.scopeFor(Permission.CLASSROOM_UPDATE)).thenReturn(BuildingScope.of(Set.of(1L)));
        when(scopeResolver.scopeFor(Permission.ALLOCATION_WRITE)).thenReturn(BuildingScope.of(Set.of(2L)));

        Page<ClassroomListItemDto> result = composer.compose(
                new PageImpl<>(List.of(inScope, outOfScope), PageRequest.of(0, 20), 2));

        assertThat(result.getContent()).extracting(ClassroomListItemDto::canUpdate).containsExactly(true, false);
        assertThat(result.getContent()).extracting(ClassroomListItemDto::canAllocate).containsExactly(false, true);
        assertThat(result.getContent()).extracting(ClassroomListItemDto::canActivate).containsExactly(false, false);
        verify(scopeResolver, times(1)).scopeFor(Permission.CLASSROOM_UPDATE);
        verify(scopeResolver, times(1)).scopeFor(Permission.CLASSROOM_ACTIVATE);
        verify(scopeResolver, times(1)).scopeFor(Permission.ALLOCATION_WRITE);
    }

    @Test
    @DisplayName("aula desactivada, aula no disponible y aula activa en la misma página: canUpdate/canAllocate siguen el estado, canActivate solo el alcance")
    void flagsDependOnClassroomStatePerItem() {
        Classroom active = classroom(1L, 1L, PermissionMode.ALL);
        Classroom deactivated = classroom(2L, 1L, PermissionMode.ALL);
        deactivated.deactivate();
        Classroom disabledInSysacad = classroom(3L, 1L, PermissionMode.ALL);
        disabledInSysacad.setSysacadHash("hash");
        disabledInSysacad.setSysacadEnabled(false);
        grantScopes(1L);

        List<ClassroomListItemDto> items = composeAll(active, deactivated, disabledInSysacad);

        assertThat(items).extracting(ClassroomListItemDto::canUpdate).containsExactly(true, false, true);
        assertThat(items).extracting(ClassroomListItemDto::canAllocate).containsExactly(true, false, false);
        assertThat(items).extracting(ClassroomListItemDto::canActivate).containsExactly(true, true, true);
    }

    @Test
    @DisplayName("aula activa en edificio desactivado: canUpdate true, canAllocate false")
    void classroomInDeactivatedBuildingCannotBeAllocated() {
        Building building = Building.builder().id(1L).name("Edificio Central").build();
        building.deactivate();
        Classroom classroom = classroom(1L, 1L, PermissionMode.ALL);
        classroom.setBuilding(building);
        grantScopes(1L);

        ClassroomListItemDto dto = composeAll(classroom).getFirst();

        assertThat(dto.canUpdate()).isTrue();
        assertThat(dto.canAllocate()).isFalse();
        assertThat(dto.canActivate()).isTrue();
    }

    private void grantScopes(long buildingId) {
        when(scopeResolver.scopeFor(Permission.CLASSROOM_UPDATE)).thenReturn(BuildingScope.of(Set.of(buildingId)));
        when(scopeResolver.scopeFor(Permission.ALLOCATION_WRITE)).thenReturn(BuildingScope.of(Set.of(buildingId)));
        when(scopeResolver.scopeFor(Permission.CLASSROOM_ACTIVATE)).thenReturn(BuildingScope.of(Set.of(buildingId)));
    }

    private List<ClassroomListItemDto> composeAll(Classroom... classrooms) {
        when(classroomResourceRepository.findByClassroomIdInAndDeletedAtIsNull(anyCollection())).thenReturn(List.of());
        when(classroomPermissionRepository.findByClassroomIdInAndDeletedAtIsNull(anyCollection())).thenReturn(List.of());
        return composer.compose(new PageImpl<>(List.of(classrooms), PageRequest.of(0, 20), classrooms.length))
                .getContent();
    }

    private ClassroomListItemDto compose(Classroom classroom) {
        Page<Classroom> page = new PageImpl<>(List.of(classroom), PageRequest.of(0, 20), 1);
        return composer.compose(page).getContent().getFirst();
    }
}
