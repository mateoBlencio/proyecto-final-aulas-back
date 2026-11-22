package ar.edu.utn.frc.siga.auth.repository;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.UserRepository.UserEmail;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserRepository.findEmailsByIdIn (integración)")
class UserRepositoryEmailsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    private User saveUser(String prefix, boolean enabled) {
        return userRepository.save(User.builder()
                .email(prefix + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar")
                .passwordHash("hash")
                .enabled(enabled)
                .firstName("Nombre")
                .lastName("Apellido")
                .build());
    }

    @Test
    @DisplayName("devuelve id y email de los usuarios pedidos, incluidos los deshabilitados, e ignora ids inexistentes")
    void returnsIdAndEmailOfRequestedUsers() {
        User enabled = saveUser("email-it-on-", true);
        User disabled = saveUser("email-it-off-", false);
        User notRequested = saveUser("email-it-other-", true);

        List<UserEmail> result = userRepository.findEmailsByIdIn(List.of(enabled.getId(), disabled.getId(), -1L));

        assertThat(result).extracting(UserEmail::getId).containsExactlyInAnyOrder(enabled.getId(), disabled.getId());
        assertThat(result).extracting(UserEmail::getEmail)
                .containsExactlyInAnyOrder(enabled.getEmail(), disabled.getEmail())
                .doesNotContain(notRequested.getEmail());
    }

    @Test
    @DisplayName("con ids que no existen devuelve lista vacía")
    void unknownIdsReturnEmpty() {
        assertThat(userRepository.findEmailsByIdIn(List.of(-1L, -2L))).isEmpty();
    }
}
