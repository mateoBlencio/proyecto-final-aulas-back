package ar.edu.utn.frc.siga.space;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.Role;
import ar.edu.utn.frc.siga.auth.security.JwtService;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.model.PermissionMode;
import ar.edu.utn.frc.siga.space.model.ResourceValueKind;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Import(IntegrationTestData.class)
@DisplayName("Classroom listado con características (integración)")
class ClassroomListApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtService jwtService;

    private MockMvc auxiliarMockMvc;

    @BeforeEach
    void setUpAuxiliar() {
        String token = jwtService.generateAccessToken("auxiliar@frc.utn.edu.ar", Set.of(Role.AUXILIAR_AULICO));
        auxiliarMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .defaultRequest(get("/").header("Authorization", "Bearer " + token))
                .build();
    }

    @Test
    @DisplayName("GET listado expone recursos, columnas derivadas, observaciones y estado")
    void list_exposesFeatureColumns() throws Exception {
        Building building = testData.edificio();
        ClassroomType tipo = testData.tipoAulaPorDefecto();
        Classroom classroom = testData.aula(building, tipo, 50);
        classroom.setObservations("Sin ventanas");
        classroomRepository.save(classroom);
        testData.recursoDeAula(classroom, testData.tipoRecurso("Cantidad de PC", ResourceValueKind.COUNT), 25);
        testData.recursoDeAula(classroom,
                testData.tipoRecurso("Proyector", ResourceValueKind.BOOLEAN), 1);
        testData.recursoDeAula(classroom,
                testData.tipoRecurso("Aire acondicionado", ResourceValueKind.BOOLEAN), 0);

        mockMvc.perform(get("/v1/classrooms").param("buildingId", String.valueOf(building.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(classroom.getId().intValue()))
                .andExpect(jsonPath("$.content[0].resources.length()").value(3))
                .andExpect(jsonPath("$.content[0].resources[?(@.name == 'Cantidad de PC')].quantity").value(org.hamcrest.Matchers.hasItem(25)))
                .andExpect(jsonPath("$.content[0].observations").value("Sin ventanas"))
                .andExpect(jsonPath("$.content[0].enabled").value(true))
                .andExpect(jsonPath("$.content[0].permissionMode").value("ALL"))
                .andExpect(jsonPath("$.content[0].allowedDisplay").value("Todas"));
    }

    @Test
    @DisplayName("includeDeactivated=true incluye las no habilitadas con enabled=false")
    void list_includeDeactivated_showsDisabledClassrooms() throws Exception {
        Building building = testData.edificio();
        ClassroomType tipo = testData.tipoAulaPorDefecto();
        Classroom classroom = testData.aula(building, tipo, 30);
        classroom.deactivate();
        classroomRepository.save(classroom);

        mockMvc.perform(get("/v1/classrooms")
                        .param("buildingId", String.valueOf(building.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + classroom.getId() + ")]").isEmpty());

        mockMvc.perform(get("/v1/classrooms")
                        .param("buildingId", String.valueOf(building.getId()))
                        .param("includeDeactivated", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + classroom.getId() + ")].enabled").value(false));
    }

    @Test
    @DisplayName("SUBSET con una materia permitida muestra su nombre en allowedDisplay")
    void list_subsetSingleSubject_showsResolvedName() throws Exception {
        Building building = testData.edificio();
        ClassroomType tipo = testData.tipoAulaPorDefecto();
        Classroom classroom = testData.aula(building, tipo, 40);
        classroom.setPermissionMode(PermissionMode.SUBSET);
        classroomRepository.save(classroom);
        IntegrationTestData.SubjectAndCommission sc = testData.materiaYComision();
        testData.permisoDeAula(classroom, sc.subjectId());

        mockMvc.perform(get("/v1/classrooms").param("buildingId", String.valueOf(building.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].permissionMode").value("SUBSET"))
                .andExpect(jsonPath("$.content[0].allowedDisplay").value("Materia-IT"))
                .andExpect(jsonPath("$.content[0].permissionTargets[0].targetId").value(sc.subjectId().intValue()))
                .andExpect(jsonPath("$.content[0].permissionTargets[0].name").value("Materia-IT"));
    }

    @Test
    @DisplayName("sort por campo no permitido responde 400")
    void list_invalidSort_returns400() throws Exception {
        mockMvc.perform(get("/v1/classrooms").param("sort", "capacidad"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("AUXILIAR_AULICO puede leer el listado")
    void list_auxiliarRole_allowed() throws Exception {
        auxiliarMockMvc.perform(get("/v1/classrooms"))
                .andExpect(status().isOk());
    }
}
