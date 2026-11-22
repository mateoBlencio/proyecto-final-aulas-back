package ar.edu.utn.frc.siga.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.RoleAssignment;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.RoleAssignmentRepository;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.security.JwtService;
import ar.edu.utn.frc.siga.auth.service.AuthService;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(IntegrationTestData.class)
@DisplayName("Cuenta propia: /v1/users/me (integración)")
class AccountApiIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Contrasena-Actual-5521";
    private static final String NEW_PASSWORD = "Cuaderno-Violeta-7391";
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleAssignmentRepository roleAssignmentRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AuthService authService;
    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ObjectMapper objectMapper;

    private static String uniqueEmail(String prefix) {
        return prefix + "." + System.nanoTime() + "@frc.utn.edu.ar";
    }

    private static String uniqueIp() {
        return "198.51.100." + (IP_SEQUENCE.incrementAndGet() % 250 + 1);
    }

    private User createUser(String email, SystemRole role, ScopeType scopeType, Long scopeId) {
        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .enabled(true)
                .firstName("Ana")
                .lastName("Prueba")
                .build());
        assign(user, role, scopeType, scopeId);
        return user;
    }

    private void assign(User user, SystemRole role, ScopeType scopeType, Long scopeId) {
        roleAssignmentRepository.save(RoleAssignment.builder()
                .user(user).role(role).scopeType(scopeType).scopeId(scopeId).build());
    }

    private MockMvc asUser(String email) {
        return MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .defaultRequest(get("/").header("Authorization", "Bearer " + jwtService.generateAccessToken(email)))
                .build();
    }

    private MockMvc anonymous() {
        return MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    private static MockHttpServletRequestBuilder fromIp(MockHttpServletRequestBuilder request, String ip) {
        return request.with(servletRequest -> {
            servletRequest.setRemoteAddr(ip);
            return servletRequest;
        });
    }

    private static String passwordBody(String current, String next) {
        return """
                {"currentPassword": "%s", "newPassword": "%s"}
                """.formatted(current, next);
    }

    private JsonNode permission(JsonNode me, Permission permission) {
        for (JsonNode grant : me.get("permissions")) {
            if (grant.get("permission").asString().equals(permission.name())) {
                return grant;
            }
        }
        return null;
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("GET /me de un auxiliar con dos edificios: buildingIds de ambos, sin USER_READ, aunque GET /v1/users dé 403")
    void me_auxiliarWithTwoBuildings_listsBothAndNoUserRead() throws Exception {
        Building first = testData.edificio();
        Building second = testData.edificio();
        String email = uniqueEmail("aux");
        User user = createUser(email, SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, first.getId());
        assign(user, SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, second.getId());
        MockMvc auxiliar = asUser(email);

        MvcResult result = auxiliar.perform(get("/v1/users/me")).andExpect(status().isOk()).andReturn();

        JsonNode me = body(result);
        assertThat(me.get("email").asString()).isEqualTo(email);
        JsonNode classroomUpdate = permission(me, Permission.CLASSROOM_UPDATE);
        assertThat(classroomUpdate).isNotNull();
        assertThat(classroomUpdate.get("allBuildings").asBoolean()).isFalse();
        List<Long> ids = new ArrayList<>();
        classroomUpdate.get("buildingIds").forEach(id -> ids.add(id.asLong()));
        assertThat(ids).containsExactlyInAnyOrder(first.getId(), second.getId()).isSorted();
        assertThat(permission(me, Permission.USER_READ)).isNull();
        assertThat(me.get("roleAssignments")).hasSize(2);
        auxiliar.perform(get("/v1/users")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /me de un CONSULTA: 200")
    void me_consulta_returns200() throws Exception {
        String email = uniqueEmail("consulta");
        createUser(email, SystemRole.CONSULTA, ScopeType.GLOBAL, null);

        MvcResult result = asUser(email).perform(get("/v1/users/me")).andExpect(status().isOk()).andReturn();

        JsonNode me = body(result);
        assertThat(permission(me, Permission.BUILDING_READ)).isNotNull();
        assertThat(permission(me, Permission.USER_MANAGE)).isNull();
    }

    @Test
    @DisplayName("GET /me sin token: 401")
    void me_withoutToken_returns401() throws Exception {
        anonymous().perform(get("/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /me de un ADMINISTRADOR: todos los permisos, allBuildings true y buildingIds vacío")
    void me_administrador_hasEveryPermissionUnrestricted() throws Exception {
        String email = uniqueEmail("admin");
        createUser(email, SystemRole.ADMINISTRADOR, ScopeType.GLOBAL, null);

        MvcResult result = asUser(email).perform(get("/v1/users/me")).andExpect(status().isOk()).andReturn();

        JsonNode permissions = body(result).get("permissions");
        assertThat(permissions).hasSize(Permission.values().length);
        List<String> names = new ArrayList<>();
        for (JsonNode grant : permissions) {
            names.add(grant.get("permission").asString());
            assertThat(grant.get("allBuildings").asBoolean()).isTrue();
            assertThat(grant.get("buildingIds")).isEmpty();
        }
        assertThat(names).containsExactlyInAnyOrderElementsOf(
                java.util.Arrays.stream(Permission.values()).map(Permission::name).toList());
        assertThat(names).isSorted();
    }

    @Test
    @DisplayName("PATCH /me con nombre en blanco: 400 con errors.firstName y no cambia nada")
    void patch_blankFirstName_returns400() throws Exception {
        String email = uniqueEmail("blank");
        User user = createUser(email, SystemRole.CONSULTA, ScopeType.GLOBAL, null);

        asUser(email).perform(patch("/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "   ", "lastName": "Nuevo"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName").exists());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getFirstName()).isEqualTo("Ana");
        assertThat(reloaded.getLastName()).isEqualTo("Prueba");
    }

    @Test
    @DisplayName("PATCH /me con nombre válido: cambia solo al usuario autenticado y recorta espacios")
    void patch_validName_changesOnlyAuthenticatedUser() throws Exception {
        String email = uniqueEmail("patch");
        User self = createUser(email, SystemRole.CONSULTA, ScopeType.GLOBAL, null);
        User other = createUser(uniqueEmail("otro"), SystemRole.CONSULTA, ScopeType.GLOBAL, null);

        asUser(email).perform(patch("/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "  Lucía ", "lastName": "Pérez  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Lucía"))
                .andExpect(jsonPath("$.lastName").value("Pérez"));

        User reloadedSelf = userRepository.findById(self.getId()).orElseThrow();
        assertThat(reloadedSelf.getFirstName()).isEqualTo("Lucía");
        assertThat(reloadedSelf.getLastName()).isEqualTo("Pérez");
        User reloadedOther = userRepository.findById(other.getId()).orElseThrow();
        assertThat(reloadedOther.getFirstName()).isEqualTo("Ana");
        assertThat(reloadedOther.getLastName()).isEqualTo("Prueba");
    }

    @Test
    @DisplayName("POST /me/password con la actual mal: 400 con errors.currentPassword y el access token sigue sirviendo")
    void password_wrongCurrent_returns400AndTokenStillWorks() throws Exception {
        String email = uniqueEmail("wrong");
        createUser(email, SystemRole.CONSULTA, ScopeType.GLOBAL, null);
        MockMvc client = asUser(email);

        client.perform(fromIp(post("/v1/users/me/password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(passwordBody("no-es-la-actual", NEW_PASSWORD)), uniqueIp()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currentPassword").exists());

        client.perform(get("/v1/users/me")).andExpect(status().isOk());
        User reloaded = userRepository.findByEmailAndEnabledTrue(email).orElseThrow();
        assertThat(passwordEncoder.matches(PASSWORD, reloaded.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("POST /me/password: cinco fallos y el siguiente intento da 429 con la forma de cuatro claves y Retry-After")
    void password_afterFiveFailures_returns429() throws Exception {
        String email = uniqueEmail("limit");
        createUser(email, SystemRole.CONSULTA, ScopeType.GLOBAL, null);
        MockMvc client = asUser(email);
        String ip = uniqueIp();

        for (int attempt = 0; attempt < 5; attempt++) {
            client.perform(fromIp(post("/v1/users/me/password")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(passwordBody("no-es-la-actual", NEW_PASSWORD)), ip))
                    .andExpect(status().isBadRequest());
        }
        MvcResult blocked = client.perform(fromIp(post("/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD, NEW_PASSWORD)), ip))
                .andExpect(status().isTooManyRequests())
                .andReturn();

        JsonNode problem = body(blocked);
        List<String> keys = new ArrayList<>();
        problem.propertyNames().forEach(keys::add);
        assertThat(keys).containsExactlyInAnyOrder("detail", "instance", "status", "title");
        assertThat(blocked.getResponse().getHeader("Retry-After")).isNotNull();
        User reloaded = userRepository.findByEmailAndEnabledTrue(email).orElseThrow();
        assertThat(passwordEncoder.matches(PASSWORD, reloaded.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("POST /me/password con éxito: devuelve sesión nueva cuyo refresh token sirve y el anterior da 401")
    void password_success_returnsFreshSessionAndRevokesOldRefresh() throws Exception {
        String email = uniqueEmail("ok");
        User user = createUser(email, SystemRole.CONSULTA, ScopeType.GLOBAL, null);
        String oldRefresh = authService.startSession(user).getRefreshToken();

        MvcResult changed = asUser(email).perform(fromIp(post("/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD, NEW_PASSWORD)), uniqueIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email))
                .andReturn();
        String newRefresh = body(changed).get("refreshToken").asString();
        assertThat(newRefresh).isNotBlank().isNotEqualTo(oldRefresh);

        User reloaded = userRepository.findByEmailAndEnabledTrue(email).orElseThrow();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, reloaded.getPasswordHash())).isTrue();

        anonymous().perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(newRefresh)))
                .andExpect(status().isOk());
        anonymous().perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(oldRefresh)))
                .andExpect(status().isUnauthorized());
    }
}
