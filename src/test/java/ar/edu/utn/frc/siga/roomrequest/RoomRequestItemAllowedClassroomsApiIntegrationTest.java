package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("GET /v1/room-requests/items/{id}/allowed-classrooms (integración)")
class RoomRequestItemAllowedClassroomsApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;

    @Test
    @DisplayName("aula activa disponible: aparece con available=true")
    void findAllowedClassrooms_returnsAvailableClassroom() throws Exception {
        Building building = testData.edificio();
        Classroom classroom = testData.aula(building);
        RoomRequestItem item = seedItem(null, false, null);

        mockMvc.perform(get("/v1/room-requests/items/" + item.getId() + "/allowed-classrooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + classroom.getId() + ")].available").value(true));
    }

    @Test
    @DisplayName("requiresComputers con un mínimo imposible de cumplir: lista vacía, 200 (no 404)")
    void findAllowedClassrooms_noMatches_returnsEmptyList() throws Exception {
        RoomRequestItem item = seedItem(true, false, 999_999);

        mockMvc.perform(get("/v1/room-requests/items/" + item.getId() + "/allowed-classrooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("id inexistente: 404")
    void findAllowedClassrooms_idInexistente_returnsNotFound() throws Exception {
        long unknownId = 999_999_000L + IntegrationTestData.nextSeq();

        mockMvc.perform(get("/v1/room-requests/items/" + unknownId + "/allowed-classrooms"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sin token: 401; con AUXILIAR_AULICO: 200 (lectura habilitada para ambos roles)")
    void authenticationAndAuthorization() throws Exception {
        RoomRequestItem item = seedItem(null, false, null);

        MockMvc anonymousMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        anonymousMockMvc.perform(get("/v1/room-requests/items/" + item.getId() + "/allowed-classrooms"))
                .andExpect(status().isUnauthorized());

        mockMvcAs("auxiliar@frc.utn.edu.ar", SystemRole.AUXILIAR_AULICO)
                .perform(get("/v1/room-requests/items/" + item.getId() + "/allowed-classrooms"))
                .andExpect(status().isOk());
    }

    private RoomRequestItem seedItem(Boolean requiresComputers, boolean requiresProjector, Integer computerCount) {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        RoomRequest request = RoomRequest.builder()
                .type(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE)
                .scope(AcademicScope.GRADO)
                .teacherName("Ada Lovelace")
                .teacherEmail("ada@frc.utn.edu.ar")
                .teacherPhone("351-1234567")
                .subjectId(academic.subjectId())
                .build();
        RoomRequestItem.RoomRequestItemBuilder builder = RoomRequestItem.builder()
                .commissionId(academic.commissionId())
                .date(LocalDate.now().plusDays(10))
                .startTime(LocalTime.of(10, 0))
                .duration(Duration.ofMinutes(120))
                .estimated(35)
                .classroomCount(1);
        if (requiresComputers != null) {
            builder.requiresComputers(requiresComputers).computerCount(computerCount);
        }
        if (requiresProjector) {
            builder.requiresProjector(true);
        }
        RoomRequestItem item = builder.build();
        request.addItem(item);
        roomRequestRepository.save(request);
        return item;
    }
}
