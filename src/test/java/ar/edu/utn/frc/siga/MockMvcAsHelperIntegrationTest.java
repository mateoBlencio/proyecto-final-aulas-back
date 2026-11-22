package ar.edu.utn.frc.siga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.auth.model.RoleAssignment;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.repository.RoleAssignmentRepository;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("mockMvcAs: el rol pedido reemplaza al guardado para el mismo email")
class MockMvcAsHelperIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleAssignmentRepository roleAssignmentRepository;

    @Test
    @DisplayName("pedir otro rol para un email existente deja solo la asignación nueva")
    void mockMvcAs_sameEmailOtherRole_replacesAssignment() throws Exception {
        String email = "helper." + System.nanoTime() + "@frc.utn.edu.ar";

        mockMvcAs(email, SystemRole.SUBSECRETARIA).perform(get("/v1/users")).andExpect(status().isOk());
        mockMvcAs(email, SystemRole.CONSULTA).perform(get("/v1/users")).andExpect(status().isForbidden());

        Long userId = userRepository.findByEmailAndEnabledTrue(email).orElseThrow().getId();
        assertThat(roleAssignmentRepository.findAllByUserId(userId))
                .extracting(RoleAssignment::getRole)
                .containsExactly(SystemRole.CONSULTA);
    }
}
