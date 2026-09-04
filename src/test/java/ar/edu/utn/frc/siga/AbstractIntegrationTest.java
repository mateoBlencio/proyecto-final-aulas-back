package ar.edu.utn.frc.siga;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ar.edu.utn.frc.siga.auth.model.Role;
import ar.edu.utn.frc.siga.auth.model.RoleAssignment;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.RoleAssignmentRepository;
import ar.edu.utn.frc.siga.auth.repository.RoleRepository;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.security.JwtService;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
    private RoleRepository roleRepository;

    @Autowired
    private RoleAssignmentRepository roleAssignmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUpAuthenticatedMockMvc() {
        mockMvc = mockMvcAs(FIXTURE_EMAIL, SystemRole.SUBSECRETARIA);
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
            Role role = roleRepository.findByName(systemRole.name())
                    .orElseThrow(() -> new IllegalStateException(
                            systemRole + " no está sembrado, RoleCatalogSeeder no corrió"));
            roleAssignmentRepository.save(RoleAssignment.builder()
                    .user(user)
                    .role(role)
                    .scopeType(scopeType)
                    .scopeId(scopeId)
                    .build());
        }
    }
}
