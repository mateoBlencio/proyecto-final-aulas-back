package ar.edu.utn.frc.siga.space;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.Role;
import ar.edu.utn.frc.siga.auth.security.JwtService;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomTypeRepository;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Import(IntegrationTestData.class)
@DisplayName("Space – endpoints de activación (integración)")
class SpaceActivationApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomTypeRepository classroomTypeRepository;

    /** MockMvc autenticado como AUXILIAR_AULICO: no tiene rol SUBSECRETARIA → 403 en /activation. */
    private MockMvc auxiliarMockMvc;

    @BeforeEach
    void setUpAuxiliarMockMvc() {
        String token = jwtService.generateAccessToken(
                "auxiliar@frc.utn.edu.ar", Set.of(Role.AUXILIAR_AULICO));
        auxiliarMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .defaultRequest(get("/").header("Authorization", "Bearer " + token))
                .build();
    }

    @Test
    @DisplayName("Building: PUT/DELETE /v1/buildings/{id}/activation (204, idempotente, 404, 403)")
    void building_activationLifecycle() throws Exception {
        Building building = testData.edificio();
        long id = building.getId();
        assertActivationLifecycle(
                "/v1/buildings/" + id + "/activation",
                "/v1/buildings/999999999/activation",
                () -> buildingRepository.findActiveById(id).isPresent());
    }

    @Test
    @DisplayName("Classroom: PUT/DELETE /v1/classrooms/{id}/activation (204, idempotente, 404, 403)")
    void classroom_activationLifecycle() throws Exception {
        Classroom classroom = testData.aula(testData.edificio());
        long id = classroom.getId();
        assertActivationLifecycle(
                "/v1/classrooms/" + id + "/activation",
                "/v1/classrooms/999999999/activation",
                () -> classroomRepository.findActiveById(id).isPresent());
    }

    @Test
    @DisplayName("ClassroomType: PUT/DELETE /v1/classroom-types/{id}/activation (204, idempotente, 404, 403)")
    void classroomType_activationLifecycle() throws Exception {
        ClassroomType type = classroomTypeRepository.save(
                ClassroomType.builder().description("Tipo-" + IntegrationTestData.nextSeq()).build());
        long id = type.getId();
        assertActivationLifecycle(
                "/v1/classroom-types/" + id + "/activation",
                "/v1/classroom-types/999999999/activation",
                () -> classroomTypeRepository.findActiveById(id).isPresent());
    }

    private void assertActivationLifecycle(String activationPath, String missingPath, BooleanSupplier active)
            throws Exception {
        assertThat(active.getAsBoolean()).isTrue();

        // DELETE → soft-delete, 204, y sale del finder de activos; segunda vez sigue 204 (idempotente)
        mockMvc.perform(delete(activationPath)).andExpect(status().isNoContent());
        assertThat(active.getAsBoolean()).isFalse();
        mockMvc.perform(delete(activationPath)).andExpect(status().isNoContent());
        assertThat(active.getAsBoolean()).isFalse();

        // PUT → restore, 204, y reingresa al finder de activos; segunda vez sigue 204 (idempotente)
        mockMvc.perform(put(activationPath)).andExpect(status().isNoContent());
        assertThat(active.getAsBoolean()).isTrue();
        mockMvc.perform(put(activationPath)).andExpect(status().isNoContent());
        assertThat(active.getAsBoolean()).isTrue();

        // id inexistente → 404 en ambos verbos
        mockMvc.perform(put(missingPath)).andExpect(status().isNotFound());
        mockMvc.perform(delete(missingPath)).andExpect(status().isNotFound());

        // sin rol SUBSECRETARIA → 403 en ambos verbos
        auxiliarMockMvc.perform(put(activationPath)).andExpect(status().isForbidden());
        auxiliarMockMvc.perform(delete(activationPath)).andExpect(status().isForbidden());
    }
}
