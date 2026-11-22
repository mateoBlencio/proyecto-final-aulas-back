package ar.edu.utn.frc.siga.space;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.RoleAssignment;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.RoleAssignmentRepository;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomDetailsUpdateDto;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomRequestDto;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.model.PermissionMode;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** Usuario mixto: AUXILIAR_AULICO en el edificio A y CONSULTA en el B. */
@Import(IntegrationTestData.class)
@DisplayName("Flags de permiso en aulas y edificios (integración)")
class ActionFlagsApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleAssignmentRepository roleAssignmentRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ClassroomRepository classroomRepository;
    @Autowired
    private BuildingRepository buildingRepository;

    private MockMvc mixedUser(Building buildingA, Building buildingB) {
        String email = "mixto-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar";
        MockMvc mvc = mockMvcAsScoped(email, SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, buildingA.getId());
        User user = userRepository.findByEmailAndEnabledTrue(email).orElseThrow();
        roleAssignmentRepository.save(RoleAssignment.builder()
                .user(user)
                .role(SystemRole.CONSULTA)
                .scopeType(ScopeType.BUILDING)
                .scopeId(buildingB.getId())
                .build());
        return mvc;
    }

    @Test
    @DisplayName("GET /v1/classrooms: canUpdate true en el aula de A y false en la de B")
    void classroomListFlagsFollowBuildingScope() throws Exception {
        Building a = testData.edificio();
        Building b = testData.edificio();
        Classroom inA = testData.aula(a);
        Classroom inB = testData.aula(b);
        MockMvc mvc = mixedUser(a, b);

        mvc.perform(get("/v1/classrooms").param("buildingId", String.valueOf(a.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + inA.getId() + ")].canUpdate").value(true))
                .andExpect(jsonPath("$.content[?(@.id == " + inA.getId() + ")].canActivate").value(true))
                .andExpect(jsonPath("$.content[?(@.id == " + inA.getId() + ")].canAllocate").value(true));
        mvc.perform(get("/v1/classrooms").param("buildingId", String.valueOf(b.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + inB.getId() + ")].canUpdate").value(false))
                .andExpect(jsonPath("$.content[?(@.id == " + inB.getId() + ")].canActivate").value(false))
                .andExpect(jsonPath("$.content[?(@.id == " + inB.getId() + ")].canAllocate").value(false));
    }

    @Test
    @DisplayName("GET /v1/classrooms/{id} del aula de B: canUpdate false; el PUT sigue respondiendo 404")
    void flagFalseAndEnforcementStillHidesClassroomOfB() throws Exception {
        Building a = testData.edificio();
        Building b = testData.edificio();
        Classroom inB = testData.aula(b);
        ClassroomType type = testData.tipoAulaPorDefecto();
        MockMvc mvc = mixedUser(a, b);

        mvc.perform(get("/v1/classrooms/" + inB.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canUpdate").value(false))
                .andExpect(jsonPath("$.canActivate").value(false))
                .andExpect(jsonPath("$.canAllocate").value(false));

        ClassroomRequestDto body = new ClassroomRequestDto(inB.getRoomNumber(), 40, type.getId(), b.getId());
        mvc.perform(put("/v1/classrooms/" + inB.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("aula desactivada en A (includeDeactivated): canUpdate y canAllocate false, canActivate true; PUT /details responde 404")
    void deactivatedClassroomFlagsMatchEnforcement() throws Exception {
        Building a = testData.edificio();
        Building b = testData.edificio();
        Classroom inA = testData.aula(a);
        classroomRepository.softDelete(inA);
        ClassroomType type = testData.tipoAulaPorDefecto();
        MockMvc mvc = mixedUser(a, b);

        mvc.perform(get("/v1/classrooms")
                        .param("buildingId", String.valueOf(a.getId()))
                        .param("includeDeactivated", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + inA.getId() + ")].canUpdate").value(false))
                .andExpect(jsonPath("$.content[?(@.id == " + inA.getId() + ")].canAllocate").value(false))
                .andExpect(jsonPath("$.content[?(@.id == " + inA.getId() + ")].canActivate").value(true));

        ClassroomDetailsUpdateDto body = new ClassroomDetailsUpdateDto(type.getId(), null, PermissionMode.ALL, null, null);
        mvc.perform(put("/v1/classrooms/" + inA.getId() + "/details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("edificio desactivado en A (includeDeactivated): canCreateClassroom false; POST /v1/classrooms responde 404")
    void deactivatedBuildingFlagMatchesEnforcement() throws Exception {
        Building a = testData.edificio("Edificio-IT", false);
        Building b = testData.edificio();
        ClassroomType type = testData.tipoAulaPorDefecto();
        MockMvc mvc = mixedUser(a, b);

        mvc.perform(get("/v1/buildings").param("name", a.getName()).param("includeDeactivated", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(a.getId()))
                .andExpect(jsonPath("$.content[0].canCreateClassroom").value(false));

        ClassroomRequestDto body = new ClassroomRequestDto((int) IntegrationTestData.nextSeq(), 40, type.getId(), a.getId());
        mvc.perform(post("/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("aula activa en edificio desactivado: canUpdate true y canAllocate false; PUT /details responde 200")
    void activeClassroomInDeactivatedBuildingCanEditDetailsButNotAllocate() throws Exception {
        Building a = testData.edificio();
        Building b = testData.edificio();
        Classroom inA = testData.aula(a);
        buildingRepository.softDelete(a);
        ClassroomType type = testData.tipoAulaPorDefecto();
        MockMvc mvc = mixedUser(a, b);

        mvc.perform(get("/v1/classrooms/" + inA.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canUpdate").value(true))
                .andExpect(jsonPath("$.canAllocate").value(false));

        ClassroomDetailsUpdateDto body = new ClassroomDetailsUpdateDto(type.getId(), null, PermissionMode.ALL, null, null);
        mvc.perform(put("/v1/classrooms/" + inA.getId() + "/details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    /** Fija la divergencia conocida: canUpdate es true pero PUT /{id} exige edificio activo. Cambiarlo si se alinea. */
    @Test
    @DisplayName("aula activa en edificio desactivado: canUpdate true pero PUT /{id} responde 404 (divergencia conocida)")
    void activeClassroomInDeactivatedBuildingFullUpdateReturnsNotFound() throws Exception {
        Building a = testData.edificio();
        Building b = testData.edificio();
        Classroom inA = testData.aula(a);
        buildingRepository.softDelete(a);
        ClassroomType type = testData.tipoAulaPorDefecto();
        MockMvc mvc = mixedUser(a, b);

        ClassroomRequestDto body = new ClassroomRequestDto(inA.getRoomNumber(), 40, type.getId(), a.getId());
        mvc.perform(put("/v1/classrooms/" + inA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /v1/classrooms/{id} del aula de A: los tres flags en true")
    void classroomOfAHasAllFlags() throws Exception {
        Building a = testData.edificio();
        Building b = testData.edificio();
        Classroom inA = testData.aula(a);
        MockMvc mvc = mixedUser(a, b);

        mvc.perform(get("/v1/classrooms/" + inA.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canUpdate").value(true))
                .andExpect(jsonPath("$.canActivate").value(true))
                .andExpect(jsonPath("$.canAllocate").value(true));
    }

    @Test
    @DisplayName("GET /v1/buildings como SUBSECRETARIA: canActivate y canCreateClassroom true")
    void buildingFlagsForSubsecretaria() throws Exception {
        Building a = testData.edificio();

        mockMvc.perform(get("/v1/buildings").param("name", a.getName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(a.getId()))
                .andExpect(jsonPath("$.content[0].canActivate").value(true))
                .andExpect(jsonPath("$.content[0].canCreateClassroom").value(true));
    }

    @Test
    @DisplayName("GET /v1/buildings como usuario mixto: canActivate false; canCreateClassroom true solo en A")
    void buildingFlagsForMixedUser() throws Exception {
        Building a = testData.edificio();
        Building b = testData.edificio();
        MockMvc mvc = mixedUser(a, b);

        mvc.perform(get("/v1/buildings").param("name", a.getName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(a.getId()))
                .andExpect(jsonPath("$.content[0].canActivate").value(false))
                .andExpect(jsonPath("$.content[0].canCreateClassroom").value(true));
        mvc.perform(get("/v1/buildings").param("name", b.getName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(b.getId()))
                .andExpect(jsonPath("$.content[0].canActivate").value(false))
                .andExpect(jsonPath("$.content[0].canCreateClassroom").value(false));
    }
}
