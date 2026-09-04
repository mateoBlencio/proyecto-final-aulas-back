package ar.edu.utn.frc.siga.auth.security;

import ar.edu.utn.frc.siga.auth.model.Role;
import ar.edu.utn.frc.siga.auth.model.RoleAssignment;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DefaultBuildingScopeResolver")
class DefaultBuildingScopeResolverTest {

    private final DefaultBuildingScopeResolver resolver = new DefaultBuildingScopeResolver();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Role role(Permission... permissions) {
        return Role.builder().id(1L).name("TEST_ROLE").systemRole(false)
                .permissions(Set.of(permissions)).build();
    }

    private RoleAssignment assignment(Role role, ScopeType scopeType, Long scopeId) {
        return RoleAssignment.builder().role(role).scopeType(scopeType).scopeId(scopeId).build();
    }

    private void authenticateAs(User user) {
        SecurityUser securityUser = SecurityUser.fromUser(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities()));
    }

    @Test
    @DisplayName("sin autenticar: scopeFor deniega, canAccess da false, requireAccess lanza")
    void sinAutenticarDeniegaTodo() {
        assertThat(resolver.scopeFor(Permission.CLASSROOM_READ)).isEqualTo(BuildingScope.denied());
        assertThat(resolver.canAccess(Permission.CLASSROOM_READ, 5L)).isFalse();
        assertThatThrownBy(() -> resolver.requireAccess(Permission.CLASSROOM_READ, 5L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("permiso BUILDING con asignación acotada: permite solo el edificio asignado")
    void permisoBuildingAcotado() {
        Role auxiliar = role(Permission.CLASSROOM_READ);
        User user = User.builder().id(1L).email("aux@frc.utn.edu.ar").enabled(true)
                .roleAssignments(List.of(assignment(auxiliar, ScopeType.BUILDING, 5L)))
                .build();
        authenticateAs(user);

        assertThat(resolver.canAccess(Permission.CLASSROOM_READ, 5L)).isTrue();
        assertThat(resolver.canAccess(Permission.CLASSROOM_READ, 7L)).isFalse();
        assertThatThrownBy(() -> resolver.requireAccess(Permission.CLASSROOM_READ, 7L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("multi-rol: dos asignaciones del mismo permiso sobre distinto edificio se unen")
    void multiRolUneAlcances() {
        Role auxiliar = role(Permission.CLASSROOM_READ);
        User user = User.builder().id(1L).email("ana@frc.utn.edu.ar").enabled(true)
                .roleAssignments(List.of(
                        assignment(auxiliar, ScopeType.BUILDING, 5L),
                        assignment(auxiliar, ScopeType.BUILDING, 9L)))
                .build();
        authenticateAs(user);

        assertThat(resolver.canAccess(Permission.CLASSROOM_READ, 5L)).isTrue();
        assertThat(resolver.canAccess(Permission.CLASSROOM_READ, 9L)).isTrue();
        assertThat(resolver.canAccess(Permission.CLASSROOM_READ, 7L)).isFalse();
    }

    @Test
    @DisplayName("permiso GLOBAL con asignación acotada a un edificio: igual queda irrestricto")
    void permisoGlobalSiempreIrrestricto() {
        Role auxiliar = role(Permission.ACADEMIC_READ);
        User user = User.builder().id(1L).email("aux@frc.utn.edu.ar").enabled(true)
                .roleAssignments(List.of(assignment(auxiliar, ScopeType.BUILDING, 5L)))
                .build();
        authenticateAs(user);

        assertThat(resolver.scopeFor(Permission.ACADEMIC_READ).isUnrestricted()).isTrue();
        assertThat(resolver.canAccess(Permission.ACADEMIC_READ, 999L)).isTrue();
    }

    @Test
    @DisplayName("asignación GLOBAL sobre un permiso BUILDING: concede todos los edificios")
    void asignacionGlobalConcedeTodo() {
        Role subsecretaria = role(Permission.CLASSROOM_READ);
        User user = User.builder().id(1L).email("sub@frc.utn.edu.ar").enabled(true)
                .roleAssignments(List.of(assignment(subsecretaria, ScopeType.GLOBAL, null)))
                .build();
        authenticateAs(user);

        assertThat(resolver.canAccess(Permission.CLASSROOM_READ, 1L)).isTrue();
        assertThat(resolver.canAccess(Permission.CLASSROOM_READ, 999L)).isTrue();
    }

    @Test
    @DisplayName("requireAccess en lote: si un solo edificio del lote está fuera de alcance, lanza")
    void requireAccessLoteFallaSiUnoEstaFueraDeAlcance() {
        Role auxiliar = role(Permission.ALLOCATION_WRITE);
        User user = User.builder().id(1L).email("aux@frc.utn.edu.ar").enabled(true)
                .roleAssignments(List.of(assignment(auxiliar, ScopeType.BUILDING, 5L)))
                .build();
        authenticateAs(user);

        assertThatThrownBy(() -> resolver.requireAccess(Permission.ALLOCATION_WRITE, List.of(5L, 7L)))
                .isInstanceOf(AccessDeniedException.class);
    }
}
