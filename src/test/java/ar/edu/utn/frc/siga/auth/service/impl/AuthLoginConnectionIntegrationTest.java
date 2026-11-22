package ar.edu.utn.frc.siga.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.dto.request.LoginRequest;
import ar.edu.utn.frc.siga.auth.dto.response.TokenResponse;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.service.AuthService;
import com.zaxxer.hikari.HikariDataSource;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** AUTH-01: el hash de la contraseña no puede correr con una conexión del pool tomada por el login. */
@DisplayName("AuthService.login y el pool de conexiones (integración)")
class AuthLoginConnectionIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "login.pool@frc.utn.edu.ar";
    private static final String PASSWORD = "TestPassword123!";

    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private PasswordEncoder passwordEncoder;

    private User user;

    @AfterEach
    void cleanUp() {
        if (user == null || user.getId() == null) {
            return;
        }
        jdbc.update("delete from refresh_token where id_usuario = ?", user.getId());
        jdbc.update("delete from usuario where id_usuario = ?", user.getId());
    }

    @Test
    @DisplayName("durante la verificación de la contraseña el pool no tiene conexiones activas, y el login devuelve tokens")
    void loginDoesNotHoldAConnectionWhileHashing() throws Exception {
        user = userRepository.save(User.builder()
                .email(EMAIL).passwordHash(passwordEncoder.encode(PASSWORD)).enabled(true)
                .firstName("Login").lastName("Pool").build());
        HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);
        AtomicInteger activeDuringHash = new AtomicInteger(-1);
        doAnswer(invocation -> {
            activeDuringHash.set(pool.getHikariPoolMXBean().getActiveConnections());
            return invocation.callRealMethod();
        }).when(passwordEncoder).matches(any(), any());

        TokenResponse response = authService.login(new LoginRequest(EMAIL, PASSWORD), new MockHttpServletRequest());

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(activeDuringHash.get()).as("la verificación del hash se ejecutó").isNotNegative();
        assertThat(activeDuringHash.get()).as("conexiones activas durante el hash").isZero();
    }
}
