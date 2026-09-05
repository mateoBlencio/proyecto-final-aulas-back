package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.Role;
import ar.edu.utn.frc.siga.auth.security.JwtService;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("GET /v1/room-requests/items/{id} (integración)")
class RoomRequestItemDetailApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private JwtService jwtService;

    @Test
    @DisplayName("id existente: devuelve la cabecera completa (con contacto del docente) y el ítem completo")
    void findById_returnsFullHeaderAndItem() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        RoomRequest request = seedRequest(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, academic.subjectId());
        RoomRequestItem item = seedItem(request, academic.commissionId(), LocalDate.now().plusDays(10),
                RoomRequestStatus.PRE_APPROVED);
        roomRequestRepository.save(request);

        mockMvc.perform(get("/v1/room-requests/items/" + item.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.id").value(request.getId()))
                .andExpect(jsonPath("$.request.teacherEmail").value("ada@frc.utn.edu.ar"))
                .andExpect(jsonPath("$.request.teacherPhone").value("351-1234567"))
                .andExpect(jsonPath("$.item.id").value(item.getId()))
                .andExpect(jsonPath("$.item.status").value("PRE_APPROVED"))
                .andExpect(jsonPath("$.item.decidedBy").value("subsecretaria@frc.utn.edu.ar"))
                .andExpect(jsonPath("$.item.observations").doesNotExist());
    }

    @Test
    @DisplayName("id inexistente: 404")
    void findById_unknownId_returnsNotFound() throws Exception {
        long unknownId = 999_999_000L + IntegrationTestData.nextSeq();

        mockMvc.perform(get("/v1/room-requests/items/" + unknownId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sin token: 401; con AUXILIAR_AULICO: 200 (lectura habilitada para ambos roles)")
    void authenticationAndAuthorization() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        RoomRequest request = seedRequest(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, academic.subjectId());
        RoomRequestItem item = seedItem(request, academic.commissionId(), LocalDate.now().plusDays(10),
                RoomRequestStatus.PENDING);
        roomRequestRepository.save(request);

        MockMvc anonymousMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        anonymousMockMvc.perform(get("/v1/room-requests/items/" + item.getId()))
                .andExpect(status().isUnauthorized());

        String auxToken = jwtService.generateAccessToken("auxiliar@frc.utn.edu.ar", Set.of(Role.AUXILIAR_AULICO));
        anonymousMockMvc.perform(get("/v1/room-requests/items/" + item.getId())
                        .header("Authorization", "Bearer " + auxToken))
                .andExpect(status().isOk());
    }

    private RoomRequest seedRequest(RoomRequestType type, Long subjectId) {
        return RoomRequest.builder()
                .type(type)
                .scope(AcademicScope.GRADO)
                .teacherName("Ada Lovelace")
                .teacherEmail("ada@frc.utn.edu.ar")
                .teacherPhone("351-1234567")
                .subjectId(subjectId)
                .build();
    }

    private RoomRequestItem seedItem(RoomRequest request, Long commissionId, LocalDate date, RoomRequestStatus status) {
        RoomRequestItem item = RoomRequestItem.builder()
                .commissionId(commissionId)
                .date(date)
                .startTime(LocalTime.of(10, 0))
                .duration(Duration.ofMinutes(120))
                .estimated(35)
                .classroomCount(1)
                .build();
        request.addItem(item);
        if (status != RoomRequestStatus.PENDING) {
            item.decide(status, "subsecretaria@frc.utn.edu.ar", "motivo de prueba", LocalDateTime.now());
        }
        return item;
    }
}
