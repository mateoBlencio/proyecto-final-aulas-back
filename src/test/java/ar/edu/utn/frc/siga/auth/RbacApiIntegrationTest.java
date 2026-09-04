package ar.edu.utn.frc.siga.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.RoleAssignmentRepository;
import ar.edu.utn.frc.siga.auth.repository.RoleRepository;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@Import(IntegrationTestData.class)
@DisplayName("RBAC: asignación/revocación de roles y alcance por edificio (integración)")
class RbacApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private RoleAssignmentRepository roleAssignmentRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;

    private static String uniqueEmail(String prefix) {
        return prefix + "." + System.nanoTime() + "@frc.utn.edu.ar";
    }

    private Long createPlainUser(String email) {
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("Test1234!"))
                .enabled(true)
                .firstName("Test")
                .lastName("User")
                .build();
        return userRepository.save(user).getId();
    }

    private String assignRoleBody(Long roleId, ScopeType scopeType, Long scopeId) {
        String scopeIdJson = scopeId == null ? "null" : String.valueOf(scopeId);
        return """
                {"roleId": %d, "scopeType": "%s", "scopeId": %s}
                """.formatted(roleId, scopeType, scopeIdJson);
    }

    // ---- asignar / revocar ----

    @Test
    @DisplayName("POST role-assignments con alcance BUILDING: 201, refleja roleName/scopeType/scopeId/scopeName")
    void assign_withBuildingScope_returnsAssignment() throws Exception {
        Building building = testData.edificio();
        Long roleId = roleRepository.findByName("AUXILIAR_AULICO").orElseThrow().getId();
        Long userId = createPlainUser(uniqueEmail("assign"));

        mockMvc.perform(post("/v1/users/{id}/role-assignments", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignRoleBody(roleId, ScopeType.BUILDING, building.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roleName").value("AUXILIAR_AULICO"))
                .andExpect(jsonPath("$.scopeType").value("BUILDING"))
                .andExpect(jsonPath("$.scopeId").value(building.getId()))
                .andExpect(jsonPath("$.scopeName").value(building.getName()));
    }

    @Test
    @DisplayName("POST role-assignments duplicada: 400, no crea una segunda fila")
    void assign_duplicate_returns400() throws Exception {
        Long roleId = roleRepository.findByName("CONSULTA").orElseThrow().getId();
        Long userId = createPlainUser(uniqueEmail("dup"));
        String body = assignRoleBody(roleId, ScopeType.GLOBAL, null);

        mockMvc.perform(post("/v1/users/{id}/role-assignments", userId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/users/{id}/role-assignments", userId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        assertThat(roleAssignmentRepository.findAllByUserId(userId)).hasSize(1);
    }

    @Test
    @DisplayName("POST role-assignments con scopeId para alcance GLOBAL: 400")
    void assign_globalWithScopeId_returns400() throws Exception {
        Long roleId = roleRepository.findByName("CONSULTA").orElseThrow().getId();
        Long userId = createPlainUser(uniqueEmail("badscope"));

        mockMvc.perform(post("/v1/users/{id}/role-assignments", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleId": %d, "scopeType": "GLOBAL", "scopeId": 1}
                                """.formatted(roleId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST role-assignments con edificio inexistente: 404")
    void assign_nonExistentBuilding_returns404() throws Exception {
        Long roleId = roleRepository.findByName("AUXILIAR_AULICO").orElseThrow().getId();
        Long userId = createPlainUser(uniqueEmail("noedif"));

        mockMvc.perform(post("/v1/users/{id}/role-assignments", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignRoleBody(roleId, ScopeType.BUILDING, 999_999_999L)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE role-assignments: 204 y la asignación desaparece")
    void revoke_removesAssignment() throws Exception {
        Long roleId = roleRepository.findByName("CONSULTA").orElseThrow().getId();
        Long userId = createPlainUser(uniqueEmail("revoke"));

        MvcResult created = mockMvc.perform(post("/v1/users/{id}/role-assignments", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignRoleBody(roleId, ScopeType.GLOBAL, null)))
                .andExpect(status().isCreated())
                .andReturn();
        long assignmentId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/v1/users/{id}/role-assignments/{assignmentId}", userId, assignmentId))
                .andExpect(status().isNoContent());

        assertThat(roleAssignmentRepository.findAllByUserId(userId)).isEmpty();
    }

    @Test
    @DisplayName("DELETE role-assignments de otro usuario: 404, no revoca")
    void revoke_wrongUser_returns404() throws Exception {
        Long roleId = roleRepository.findByName("CONSULTA").orElseThrow().getId();
        Long ownerId = createPlainUser(uniqueEmail("owner"));
        Long otherId = createPlainUser(uniqueEmail("other"));

        MvcResult created = mockMvc.perform(post("/v1/users/{id}/role-assignments", ownerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignRoleBody(roleId, ScopeType.GLOBAL, null)))
                .andExpect(status().isCreated())
                .andReturn();
        long assignmentId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/v1/users/{id}/role-assignments/{assignmentId}", otherId, assignmentId))
                .andExpect(status().isNotFound());

        assertThat(roleAssignmentRepository.findAllByUserId(ownerId)).hasSize(1);
    }

    @Test
    @DisplayName("DELETE role-assignments propia: 400, no se puede auto-revocar")
    void revoke_ownAssignment_returns400() throws Exception {
        String email = uniqueEmail("self");
        MockMvc selfMockMvc = mockMvcAsScoped(email, SystemRole.SUBSECRETARIA, ScopeType.GLOBAL, null);
        Long userId = userRepository.findByEmailAndEnabledTrue(email).orElseThrow().getId();
        Long assignmentId = roleAssignmentRepository.findAllByUserId(userId).getFirst().getId();

        selfMockMvc.perform(delete("/v1/users/{id}/role-assignments/{assignmentId}", userId, assignmentId))
                .andExpect(status().isBadRequest());

        assertThat(roleAssignmentRepository.findAllByUserId(userId)).hasSize(1);
    }

    // ---- multi-rol: unión de alcances ----

    @Test
    @DisplayName("multi-rol: la unión de dos asignaciones deja ver aulas de ambos edificios, no de un tercero")
    void multiRole_unionOfScopes_seesBothBuildings() throws Exception {
        Building b1 = testData.edificio();
        Building b2 = testData.edificio();
        Building ajeno = testData.edificio();
        Classroom c1 = testData.aula(b1);
        Classroom c2 = testData.aula(b2);
        testData.aula(ajeno);

        String email = uniqueEmail("multirol");
        MockMvc userMockMvc = mockMvcAsScoped(email, SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, b1.getId());
        Long userId = userRepository.findByEmailAndEnabledTrue(email).orElseThrow().getId();
        Long roleId = roleRepository.findByName("AUXILIAR_AULICO").orElseThrow().getId();

        mockMvc.perform(post("/v1/users/{id}/role-assignments", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignRoleBody(roleId, ScopeType.BUILDING, b2.getId())))
                .andExpect(status().isCreated());

        userMockMvc.perform(get("/v1/classrooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + c1.getId() + ")]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.id == " + c2.getId() + ")]").isNotEmpty());
    }

    // ---- auxiliar contra edificio ajeno ----

    @Test
    @DisplayName("auxiliar contra edificio ajeno: 403 al escribir, 404 al detalle, vacío en listado")
    void auxiliar_foreignBuilding_deniedEverywhere() throws Exception {
        Building own = testData.edificio();
        Building foreign = testData.edificio();
        Classroom foreignClassroom = testData.aula(foreign);

        MockMvc auxMockMvc = mockMvcAsScoped(
                uniqueEmail("scoped"), SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, own.getId());

        auxMockMvc.perform(post("/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomNumber": 999, "capacity": 10, "classroomTypeId": %d, "buildingId": %d}
                                """.formatted(testData.tipoAulaNormal().getId(), foreign.getId())))
                .andExpect(status().isForbidden());

        auxMockMvc.perform(get("/v1/classrooms/{id}", foreignClassroom.getId()))
                .andExpect(status().isNotFound());

        auxMockMvc.perform(get("/v1/classrooms").param("buildingId", String.valueOf(foreign.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    // ---- CONSULTA no escribe ----

    @Test
    @DisplayName("CONSULTA no puede crear aulas aunque tenga alcance sobre el edificio")
    void consulta_cannotWrite() throws Exception {
        Building building = testData.edificio();
        MockMvc consultaMockMvc = mockMvcAsScoped(
                uniqueEmail("consulta"), SystemRole.CONSULTA, ScopeType.BUILDING, building.getId());

        consultaMockMvc.perform(post("/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomNumber": 111, "capacity": 20, "classroomTypeId": %d, "buildingId": %d}
                                """.formatted(testData.tipoAulaNormal().getId(), building.getId())))
                .andExpect(status().isForbidden());

        consultaMockMvc.perform(get("/v1/classrooms").param("buildingId", String.valueOf(building.getId())))
                .andExpect(status().isOk());
    }
}
