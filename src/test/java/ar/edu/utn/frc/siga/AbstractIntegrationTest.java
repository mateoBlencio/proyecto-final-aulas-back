package ar.edu.utn.frc.siga;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ar.edu.utn.frc.siga.auth.model.RoleAssignment;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.RoleAssignmentRepository;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.security.JwtService;
import ar.edu.utn.frc.siga.auth.security.SecurityUser;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("integration")
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    private static final String FIXTURE_EMAIL = "integration-test@frc.utn.edu.ar";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleAssignmentRepository roleAssignmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUpAuthenticatedMockMvc() {
        mockMvc = mockMvcAs(FIXTURE_EMAIL, SystemRole.SUBSECRETARIA);
    }

    /**
     * Corre {@code action} con el {@link SecurityContextHolder} poblado por el usuario fixture
     * (SUBSECRETARIA / GLOBAL). Para los tests de integración que invocan servicios acotados por
     * edificio directamente (no vía {@code mockMvc}), donde no hay filtro que establezca el contexto.
     */
    protected <T> T asFixtureUser(Supplier<T> action) {
        User fixture = userRepository.findByEmailAndEnabledTrue(FIXTURE_EMAIL).orElseThrow();
        SecurityUser principal = SecurityUser.fromUser(fixture);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    protected void asFixtureUser(Runnable action) {
        asFixtureUser(() -> {
            action.run();
            return null;
        });
    }

    protected MockMvc mockMvcAs(String email, SystemRole systemRole) {
        return mockMvcAsScoped(email, systemRole, ScopeType.GLOBAL, null);
    }

    protected MockMvc mockMvcAsScoped(String email, SystemRole systemRole, ScopeType scopeType, Long scopeId) {
        ensureUserWithRole(email, systemRole, scopeType, scopeId);
        String token = jwtService.generateAccessToken(email);

        return MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .defaultRequest(get("/").header("Authorization", "Bearer " + token))
                .build();
    }

    private void ensureUserWithRole(String email, SystemRole systemRole, ScopeType scopeType, Long scopeId) {
        User user = userRepository.findByEmailAndEnabledTrue(email).orElseGet(() -> {
            User created = User.builder()
                    .email(email)
                    .passwordHash(passwordEncoder.encode("integration-test-fixture-password"))
                    .enabled(true)
                    .firstName("Fixture")
                    .lastName("User")
                    .build();
            return userRepository.save(created);
        });

        if (user.getRoleAssignments().isEmpty()) {
            roleAssignmentRepository.save(RoleAssignment.builder()
                    .user(user)
                    .role(systemRole)
                    .scopeType(scopeType)
                    .scopeId(scopeId)
                    .build());
        }
    }
}
