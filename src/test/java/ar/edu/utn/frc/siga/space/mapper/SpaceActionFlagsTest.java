package ar.edu.utn.frc.siga.space.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.auth.security.DefaultBuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.common.security.SystemScope;
import ar.edu.utn.frc.siga.space.SpaceTestData;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@DisplayName("SpaceActionFlags")
class SpaceActionFlagsTest {

    @Mock
    private BuildingScopeResolver scopeResolver;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static Building building(long id) {
        return SpaceTestData.building().id(id).build();
    }

    private static Building deactivatedBuilding(long id) {
        return SpaceTestData.deactivated(building(id));
    }

    private static Classroom classroomIn(long buildingId) {
        return SpaceTestData.classroom().building(building(buildingId)).build();
    }

    private static Classroom deactivatedClassroomIn(long buildingId) {
        Classroom classroom = classroomIn(buildingId);
        classroom.deactivate();
        return classroom;
    }

    /** Aula activa en edificio activo, pero con unavailableReason por "habilitada = N" en Sysacad. */
    private static Classroom disabledInSysacadClassroomIn(long buildingId) {
        return SpaceTestData.classroom()
                .building(building(buildingId))
                .sysacadHash("hash")
                .sysacadEnabled(false)
                .build();
    }

    @Test
    @DisplayName("canUpdateClassroom: true en el edificio dentro del alcance de CLASSROOM_UPDATE, false en otro")
    void canUpdateClassroomFollowsScope() {
        when(scopeResolver.scopeFor(Permission.CLASSROOM_UPDATE)).thenReturn(BuildingScope.of(Set.of(1L)));
        SpaceActionFlags flags = new SpaceActionFlags(scopeResolver);

        assertThat(flags.canUpdateClassroom(classroomIn(1L))).isTrue();
        assertThat(flags.canUpdateClassroom(classroomIn(2L))).isFalse();
    }

    @Test
    @DisplayName("canUpdateClassroom: aula desactivada en el edificio del alcance da false; la activa da true")
    void canUpdateClassroomIsFalseForDeactivatedClassroom() {
        when(scopeResolver.scopeFor(Permission.CLASSROOM_UPDATE)).thenReturn(BuildingScope.of(Set.of(1L)));
        SpaceActionFlags flags = new SpaceActionFlags(scopeResolver);

        assertThat(flags.canUpdateClassroom(deactivatedClassroomIn(1L))).isFalse();
        assertThat(flags.canUpdateClassroom(classroomIn(1L))).isTrue();
    }

    @Test
    @DisplayName("canUpdateClassroom: aula activa con unavailableReason sigue en true (editar solo exige aula activa)")
    void canUpdateClassroomIgnoresUnavailableReasonOfActiveClassroom() {
        Classroom disabled = disabledInSysacadClassroomIn(1L);
        when(scopeResolver.scopeFor(Permission.CLASSROOM_UPDATE)).thenReturn(BuildingScope.of(Set.of(1L)));
        SpaceActionFlags flags = new SpaceActionFlags(scopeResolver);

        assertThat(disabled.unavailableReason()).as("precondición").isNotNull();
        assertThat(flags.canUpdateClassroom(disabled)).isTrue();
    }

    @Test
    @DisplayName("canAllocateClassroom: aula desactivada, con unavailableReason o en edificio desactivado da false aunque esté en alcance")
    void canAllocateClassroomIsFalseWhenClassroomIsNotAvailable() {
        when(scopeResolver.scopeFor(Permission.ALLOCATION_WRITE)).thenReturn(BuildingScope.of(Set.of(1L)));
        SpaceActionFlags flags = new SpaceActionFlags(scopeResolver);
        Classroom inDeactivatedBuilding = SpaceTestData.classroom().building(deactivatedBuilding(1L)).build();

        assertThat(flags.canAllocateClassroom(classroomIn(1L))).as("disponible").isTrue();
        assertThat(flags.canAllocateClassroom(deactivatedClassroomIn(1L))).as("aula desactivada").isFalse();
        assertThat(flags.canAllocateClassroom(disabledInSysacadClassroomIn(1L)))
                .as("habilitada = N en Sysacad").isFalse();
        assertThat(flags.canAllocateClassroom(inDeactivatedBuilding)).as("edificio desactivado").isFalse();
    }

    @Test
    @DisplayName("canAllocateClassroom: aula disponible fuera del alcance de ALLOCATION_WRITE da false")
    void canAllocateClassroomIsFalseOutOfScopeEvenIfAvailable() {
        when(scopeResolver.scopeFor(Permission.ALLOCATION_WRITE)).thenReturn(BuildingScope.of(Set.of(1L)));
        SpaceActionFlags flags = new SpaceActionFlags(scopeResolver);

        assertThat(flags.canAllocateClassroom(classroomIn(2L))).isFalse();
    }

    @Test
    @DisplayName("canCreateClassroom: edificio desactivado da false aunque esté en alcance; el activo da true")
    void canCreateClassroomIsFalseForDeactivatedBuilding() {
        when(scopeResolver.scopeFor(Permission.CLASSROOM_CREATE)).thenReturn(BuildingScope.of(Set.of(1L)));
        SpaceActionFlags flags = new SpaceActionFlags(scopeResolver);

        assertThat(flags.canCreateClassroom(deactivatedBuilding(1L))).isFalse();
        assertThat(flags.canCreateClassroom(building(1L))).isTrue();
    }

    @Test
    @DisplayName("canActivateClassroom depende solo del alcance: aula desactivada en alcance da true")
    void canActivateClassroomIgnoresClassroomState() {
        when(scopeResolver.scopeFor(Permission.CLASSROOM_ACTIVATE)).thenReturn(BuildingScope.of(Set.of(1L)));
        SpaceActionFlags flags = new SpaceActionFlags(scopeResolver);

        assertThat(flags.canActivateClassroom(deactivatedClassroomIn(1L).getBuilding().getId())).isTrue();
    }

    @Test
    @DisplayName("cada flag consulta su propio permiso")
    void eachFlagUsesItsOwnPermission() {
        when(scopeResolver.scopeFor(Permission.CLASSROOM_UPDATE)).thenReturn(BuildingScope.of(Set.of(1L)));
        when(scopeResolver.scopeFor(Permission.CLASSROOM_ACTIVATE)).thenReturn(BuildingScope.of(Set.of(2L)));
        when(scopeResolver.scopeFor(Permission.ALLOCATION_WRITE)).thenReturn(BuildingScope.of(Set.of(3L)));
        when(scopeResolver.scopeFor(Permission.CLASSROOM_CREATE)).thenReturn(BuildingScope.of(Set.of(4L)));
        SpaceActionFlags flags = new SpaceActionFlags(scopeResolver);

        assertThat(flags.canUpdateClassroom(classroomIn(1L))).isTrue();
        assertThat(flags.canUpdateClassroom(classroomIn(2L))).isFalse();
        assertThat(flags.canActivateClassroom(2L)).isTrue();
        assertThat(flags.canActivateClassroom(1L)).isFalse();
        assertThat(flags.canAllocateClassroom(classroomIn(3L))).isTrue();
        assertThat(flags.canAllocateClassroom(classroomIn(1L))).isFalse();
        assertThat(flags.canCreateClassroom(building(4L))).isTrue();
        assertThat(flags.canCreateClassroom(building(1L))).isFalse();
    }

    @Test
    @DisplayName("canActivateBuilding: true solo con alcance irrestricto de BUILDING_ACTIVATE")
    void canActivateBuildingRequiresUnrestrictedScope() {
        when(scopeResolver.scopeFor(Permission.BUILDING_ACTIVATE))
                .thenReturn(BuildingScope.unrestricted(), BuildingScope.denied(), BuildingScope.of(Set.of(1L)));
        SpaceActionFlags flags = new SpaceActionFlags(scopeResolver);

        assertThat(flags.canActivateBuilding(1L)).isTrue();
        assertThat(flags.canActivateBuilding(1L)).isFalse();
        assertThat(flags.canActivateBuilding(1L)).isFalse();
    }

    @Test
    @DisplayName("bajo SystemScope todos los flags son false y el resolver no se consulta")
    void allFlagsFalseUnderSystemScope() {
        SpaceActionFlags flags = new SpaceActionFlags(scopeResolver);

        SystemScope.run(() -> {
            assertThat(flags.canUpdateClassroom(classroomIn(1L))).isFalse();
            assertThat(flags.canActivateClassroom(1L)).isFalse();
            assertThat(flags.canAllocateClassroom(classroomIn(1L))).isFalse();
            assertThat(flags.canCreateClassroom(building(1L))).isFalse();
            assertThat(flags.canActivateBuilding(1L)).isFalse();
        });

        verifyNoInteractions(scopeResolver);
    }

    @Test
    @DisplayName("bajo SystemScope con el resolver real (que da alcance irrestricto) los flags siguen en false")
    void allFlagsFalseUnderSystemScopeWithRealResolver() {
        SpaceActionFlags flags = new SpaceActionFlags(new DefaultBuildingScopeResolver());

        SystemScope.run(() -> {
            assertThat(new DefaultBuildingScopeResolver().scopeFor(Permission.CLASSROOM_UPDATE).isUnrestricted())
                    .as("precondición: el resolver real abre el alcance bajo SystemScope").isTrue();
            assertThat(flags.canUpdateClassroom(classroomIn(1L))).isFalse();
            assertThat(flags.canActivateBuilding(1L)).isFalse();
        });
    }

    @Test
    @DisplayName("sin SecurityContext (resolver real) todos los flags son false")
    void allFlagsFalseWithoutSecurityContext() {
        SecurityContextHolder.clearContext();
        SpaceActionFlags flags = new SpaceActionFlags(new DefaultBuildingScopeResolver());

        assertThat(flags.canUpdateClassroom(classroomIn(1L))).isFalse();
        assertThat(flags.canActivateClassroom(1L)).isFalse();
        assertThat(flags.canAllocateClassroom(classroomIn(1L))).isFalse();
        assertThat(flags.canCreateClassroom(building(1L))).isFalse();
        assertThat(flags.canActivateBuilding(1L)).isFalse();
    }

    @Test
    @DisplayName("fuera de SystemScope el flag vuelve a seguir al resolver (el ThreadLocal se restaura)")
    void systemScopeDoesNotLeak() {
        when(scopeResolver.scopeFor(Permission.CLASSROOM_UPDATE)).thenReturn(BuildingScope.of(Set.of(1L)));
        SpaceActionFlags flags = new SpaceActionFlags(scopeResolver);

        SystemScope.run(() -> assertThat(flags.canUpdateClassroom(classroomIn(1L))).isFalse());

        assertThat(flags.canUpdateClassroom(classroomIn(1L))).isTrue();
    }
}
