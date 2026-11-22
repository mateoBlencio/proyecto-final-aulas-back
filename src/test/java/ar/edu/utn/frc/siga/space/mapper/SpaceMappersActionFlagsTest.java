package ar.edu.utn.frc.siga.space.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.space.SpaceTestData;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** Verifica que cada {@code @Mapping} de los mappers apunte al método de {@link SpaceActionFlags} correcto. */
@SpringJUnitConfig(classes = {ClassroomMapperImpl.class, BuildingMapperImpl.class, SpaceActionFlags.class})
@DisplayName("ClassroomMapper y BuildingMapper: flags de permiso")
class SpaceMappersActionFlagsTest {

    @Autowired
    private ClassroomMapper classroomMapper;
    @Autowired
    private BuildingMapper buildingMapper;
    @MockitoBean
    private BuildingScopeResolver scopeResolver;

    @BeforeEach
    void resetScopes() {
        Mockito.reset(scopeResolver);
        for (Permission permission : Permission.values()) {
            when(scopeResolver.scopeFor(permission)).thenReturn(BuildingScope.denied());
        }
    }

    @Test
    @DisplayName("aula del edificio 1: canUpdate sigue CLASSROOM_UPDATE {1}, canAllocate ALLOCATION_WRITE {2}, canActivate CLASSROOM_ACTIVATE {1}")
    void classroomFlagsComeFromTheirOwnPermission() {
        when(scopeResolver.scopeFor(Permission.CLASSROOM_UPDATE)).thenReturn(BuildingScope.of(Set.of(1L)));
        when(scopeResolver.scopeFor(Permission.ALLOCATION_WRITE)).thenReturn(BuildingScope.of(Set.of(2L)));
        when(scopeResolver.scopeFor(Permission.CLASSROOM_ACTIVATE)).thenReturn(BuildingScope.of(Set.of(1L)));
        Classroom classroom = SpaceTestData.classroom()
                .building(Building.builder().id(1L).name("Edificio Central").build()).build();

        ClassroomResponseDto dto = classroomMapper.toDto(classroom);

        assertThat(dto.canUpdate()).isTrue();
        assertThat(dto.canAllocate()).isFalse();
        assertThat(dto.canActivate()).isTrue();
    }

    @Test
    @DisplayName("aula de un edificio fuera del alcance: los tres flags en false")
    void classroomOutOfScopeHasNoFlags() {
        when(scopeResolver.scopeFor(Permission.CLASSROOM_UPDATE)).thenReturn(BuildingScope.of(Set.of(1L)));
        when(scopeResolver.scopeFor(Permission.ALLOCATION_WRITE)).thenReturn(BuildingScope.of(Set.of(1L)));
        when(scopeResolver.scopeFor(Permission.CLASSROOM_ACTIVATE)).thenReturn(BuildingScope.of(Set.of(1L)));
        Classroom classroom = SpaceTestData.classroom()
                .building(Building.builder().id(2L).name("Otro").build()).build();

        ClassroomResponseDto dto = classroomMapper.toDto(classroom);

        assertThat(dto.canUpdate()).isFalse();
        assertThat(dto.canAllocate()).isFalse();
        assertThat(dto.canActivate()).isFalse();
    }

    @Test
    @DisplayName("edificio: canCreateClassroom sigue CLASSROOM_CREATE {1} (true para id 1, false para id 2)")
    void buildingCanCreateClassroomFollowsScope() {
        when(scopeResolver.scopeFor(Permission.CLASSROOM_CREATE)).thenReturn(BuildingScope.of(Set.of(1L)));

        BuildingResponseDto inScope = buildingMapper.toDto(Building.builder().id(1L).name("A").build());
        BuildingResponseDto outOfScope = buildingMapper.toDto(Building.builder().id(2L).name("B").build());

        assertThat(inScope.canCreateClassroom()).isTrue();
        assertThat(outOfScope.canCreateClassroom()).isFalse();
        assertThat(inScope.canActivate()).isFalse();
    }

    @Test
    @DisplayName("edificio: canActivate sigue BUILDING_ACTIVATE irrestricto, independiente del edificio")
    void buildingCanActivateRequiresUnrestrictedScope() {
        when(scopeResolver.scopeFor(Permission.BUILDING_ACTIVATE)).thenReturn(BuildingScope.unrestricted());

        BuildingResponseDto dto = buildingMapper.toDto(Building.builder().id(2L).name("B").build());

        assertThat(dto.canActivate()).isTrue();
        assertThat(dto.canCreateClassroom()).isFalse();
    }

    @Test
    @DisplayName("aula desactivada en edificio dentro de todos los alcances: canUpdate y canAllocate false, canActivate true")
    void deactivatedClassroomKeepsOnlyActivateFlag() {
        grantAllClassroomScopes(1L);
        Classroom classroom = SpaceTestData.classroom()
                .building(Building.builder().id(1L).name("Edificio Central").build()).build();
        classroom.deactivate();

        ClassroomResponseDto dto = classroomMapper.toDto(classroom);

        assertThat(dto.canUpdate()).isFalse();
        assertThat(dto.canAllocate()).isFalse();
        assertThat(dto.canActivate()).isTrue();
    }

    @Test
    @DisplayName("aula activa en edificio desactivado: canUpdate true (solo exige aula activa), canAllocate false")
    void activeClassroomInDeactivatedBuildingCannotBeAllocated() {
        grantAllClassroomScopes(1L);
        Building building = Building.builder().id(1L).name("Edificio Central").build();
        building.deactivate();
        Classroom classroom = SpaceTestData.classroom().building(building).build();

        ClassroomResponseDto dto = classroomMapper.toDto(classroom);

        assertThat(dto.canUpdate()).isTrue();
        assertThat(dto.canAllocate()).isFalse();
        assertThat(dto.canActivate()).isTrue();
    }

    @Test
    @DisplayName("edificio desactivado con CLASSROOM_CREATE y BUILDING_ACTIVATE: canCreateClassroom false, canActivate true")
    void deactivatedBuildingCannotCreateClassrooms() {
        when(scopeResolver.scopeFor(Permission.CLASSROOM_CREATE)).thenReturn(BuildingScope.of(Set.of(1L)));
        when(scopeResolver.scopeFor(Permission.BUILDING_ACTIVATE)).thenReturn(BuildingScope.unrestricted());
        Building building = Building.builder().id(1L).name("A").build();
        building.deactivate();

        BuildingResponseDto dto = buildingMapper.toDto(building);

        assertThat(dto.canCreateClassroom()).isFalse();
        assertThat(dto.canActivate()).isTrue();
    }

    private void grantAllClassroomScopes(Long buildingId) {
        for (Permission permission : Set.of(Permission.CLASSROOM_UPDATE, Permission.ALLOCATION_WRITE,
                Permission.CLASSROOM_ACTIVATE)) {
            when(scopeResolver.scopeFor(permission)).thenReturn(BuildingScope.of(Set.of(buildingId)));
        }
    }
}
