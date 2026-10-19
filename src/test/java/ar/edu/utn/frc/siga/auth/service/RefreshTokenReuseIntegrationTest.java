package ar.edu.utn.frc.siga.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.dto.request.RefreshTokenRequest;
import ar.edu.utn.frc.siga.auth.exception.InvalidRefreshTokenException;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.RefreshTokenRepository;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Not @Transactional on purpose: the cascade revocation must be committed in the DB
 * even though the service throws afterwards.
 */
class RefreshTokenReuseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private User user;

    @BeforeEach
    void createUser() {
        user = userRepository.save(User.builder()
                .email("reuse.test@frc.utn.edu.ar")
                .passwordHash("irrelevant")
                .enabled(true)
                .firstName("Reuse")
                .lastName("Test")
                .build());
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("update refresh_token set reemplazado_por = null where id_usuario = ?", user.getId());
        jdbc.update("delete from refresh_token where id_usuario = ?", user.getId());
        jdbc.update("delete from usuario where id_usuario = ?", user.getId());
    }

    @Test
    void refresh_reusedTokenOutsideGraceWindow_revokesTheWholeFamilyInTheDb() {
        String first = refreshTokenService.issue(user).rawToken();
        String second = authService.refresh(new RefreshTokenRequest(first)).getRefreshToken();
        jdbc.update("update refresh_token set fecha_revocacion = now() - interval '1 hour' where id_usuario = ? and revocado",
                user.getId());

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(first)))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(user.getId()))
                .allMatch(t -> Boolean.TRUE.equals(t.getRevoked()))).isTrue();
        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(second)))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_disabledUser_revokesAllTokensInTheDb() {
        String raw = refreshTokenService.issue(user).rawToken();
        jdbc.update("update usuario set habilitado = false where id_usuario = ?", user.getId());

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(raw)))
                .isInstanceOf(InvalidRefreshTokenException.class);

        Integer active = jdbc.queryForObject(
                "select count(*) from refresh_token where id_usuario = ? and not revocado", Integer.class, user.getId());
        assertThat(active).isZero();
    }
}
