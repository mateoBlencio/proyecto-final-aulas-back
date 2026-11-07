package ar.edu.utn.frc.siga.auth;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.dto.response.UserResponseDto;
import ar.edu.utn.frc.siga.auth.model.RoleAssignment;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.RoleAssignmentRepository;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.service.UserService;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(IntegrationTestData.class)
@DisplayName("UserService.findByRole (integración)")
class UserServiceFindByRoleIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleAssignmentRepository roleAssignmentRepository;
    @Autowired
    private IntegrationTestData testData;

    private String uniqueEmail(String prefix) {
        return prefix + "-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar";
    }

    private void createUser(String email, boolean enabled, SystemRole role, ScopeType scope, Long scopeId) {
        User user = userRepository.save(User.builder().email(email).passwordHash("x").enabled(enabled)
                .firstName("Prueba").lastName("Rol").build());
        roleAssignmentRepository.save(RoleAssignment.builder().user(user).role(role).scopeType(scope)
                .scopeId(scopeId).build());
    }

    private List<String> emailsWithRole(SystemRole role) {
        return userService.findByRole(role).stream().map(UserResponseDto::email).toList();
    }

    @Test
    @DisplayName("trae los habilitados con el rol, de cualquier alcance, y deja afuera a los deshabilitados y a otros roles")
    void returnsEnabledUsersOfAnyScope() {
        Building first = testData.edificio();
        Building second = testData.edificio();
        String inFirst = uniqueEmail("aux-uno");
        String inSecond = uniqueEmail("aux-dos");
        String disabled = uniqueEmail("aux-deshabilitado");
        String subsecretaria = uniqueEmail("sub");
        createUser(inFirst, true, SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, first.getId());
        createUser(inSecond, true, SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, second.getId());
        createUser(disabled, false, SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, first.getId());
        createUser(subsecretaria, true, SystemRole.SUBSECRETARIA, ScopeType.GLOBAL, null);

        assertThat(emailsWithRole(SystemRole.AUXILIAR_AULICO)).contains(inFirst, inSecond)
                .doesNotContain(disabled, subsecretaria);
        assertThat(emailsWithRole(SystemRole.SUBSECRETARIA)).contains(subsecretaria).doesNotContain(inFirst);
    }

    @Test
    @DisplayName("un usuario deshabilitado con rol de Subsecretaría no aparece")
    void disabledSubsecretariaIsExcluded() {
        String disabled = uniqueEmail("sub-deshabilitada");
        createUser(disabled, false, SystemRole.SUBSECRETARIA, ScopeType.GLOBAL, null);

        assertThat(emailsWithRole(SystemRole.SUBSECRETARIA)).doesNotContain(disabled);
    }

    @Test
    @DisplayName("un usuario con el rol en dos alcances aparece una sola vez")
    void userWithTwoAssignmentsAppearsOnce() {
        Building first = testData.edificio();
        Building second = testData.edificio();
        String email = uniqueEmail("aux-dos-edificios");
        User user = userRepository.save(User.builder().email(email).passwordHash("x").enabled(true)
                .firstName("Doble").lastName("Alcance").build());
        for (Building building : List.of(first, second)) {
            roleAssignmentRepository.save(RoleAssignment.builder().user(user).role(SystemRole.AUXILIAR_AULICO)
                    .scopeType(ScopeType.BUILDING).scopeId(building.getId()).build());
        }

        assertThat(emailsWithRole(SystemRole.AUXILIAR_AULICO)).filteredOn(email::equals).hasSize(1);
    }
}
