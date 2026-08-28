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

/**
 * Cada test escala su búsqueda por el {@code subjectId} único de
 * {@link IntegrationTestData#materiaYComision()}: la base no se limpia entre tests.
 */
@Import(IntegrationTestData.class)
@DisplayName("GET /v1/room-requests/items/status-counts (integración)")
class RoomRequestItemStatusCountsApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private JwtService jwtService;

    @Test
    @DisplayName("devuelve los 3 estados en orden del enum, con 0 explícito, ignorando el param statuses")
    void returnsCountPerStatus() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        RoomRequest request = seedRequest(RoomRequestType.PARTIAL_EXAM, academic.subjectId());
        seedItem(request, academic.commissionId(), LocalDate.now().plusDays(10), RoomRequestStatus.PENDING);
        seedItem(request, academic.commissionId(), LocalDate.now().plusDays(11), RoomRequestStatus.PENDING);
        seedItem(request, academic.commissionId(), LocalDate.now().plusDays(12), RoomRequestStatus.PRE_APPROVED);
        roomRequestRepository.save(request);

        mockMvc.perform(get("/v1/room-requests/items/status-counts")
                        .param("subjectId", String.valueOf(academic.subjectId()))
                        .param("statuses", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].count").value(2))
                .andExpect(jsonPath("$[1].status").value("PRE_APPROVED"))
                .andExpect(jsonPath("$[1].count").value(1))
                .andExpect(jsonPath("$[2].status").value("CANCELLED"))
                .andExpect(jsonPath("$[2].count").value(0));
    }

    @Test
    @DisplayName("aplica types e includePast igual que el listado")
    void appliesTypeAndIncludePastFilters() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();

        RoomRequest partial = seedRequest(RoomRequestType.PARTIAL_EXAM, academic.subjectId());
        seedItem(partial, academic.commissionId(), LocalDate.now().plusDays(5), RoomRequestStatus.PENDING);
        seedItem(partial, academic.commissionId(), LocalDate.now().minusDays(5), RoomRequestStatus.CANCELLED);
        roomRequestRepository.save(partial);

        RoomRequest conference = seedRequest(RoomRequestType.CONFERENCE, academic.subjectId());
        seedItem(conference, null, LocalDate.now().plusDays(6), RoomRequestStatus.PENDING);
        roomRequestRepository.save(conference);

        mockMvc.perform(get("/v1/room-requests/items/status-counts")
                        .param("subjectId", String.valueOf(academic.subjectId()))
                        .param("types", "PARTIAL_EXAM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].count").value(1))
                .andExpect(jsonPath("$[2].count").value(0));

        mockMvc.perform(get("/v1/room-requests/items/status-counts")
                        .param("subjectId", String.valueOf(academic.subjectId()))
                        .param("types", "PARTIAL_EXAM")
                        .param("includePast", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].count").value(1))
                .andExpect(jsonPath("$[2].count").value(1));
    }

    @Test
    @DisplayName("sin token: 401; con AUXILIAR_AULICO: 200 (lectura habilitada para ambos roles)")
    void authenticationAndAuthorization() throws Exception {
        MockMvc anonymousMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        anonymousMockMvc.perform(get("/v1/room-requests/items/status-counts"))
                .andExpect(status().isUnauthorized());

        String auxToken = jwtService.generateAccessToken("auxiliar@frc.utn.edu.ar", Set.of(Role.AUXILIAR_AULICO));
        anonymousMockMvc.perform(get("/v1/room-requests/items/status-counts")
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
                .createdAt(LocalDateTime.now())
                .build();
    }

    /** Statuses distintos de PENDING pasan por {@code decide(...)}: el check constraint exige decidedBy/decidedAt. */
    private RoomRequestItem seedItem(RoomRequest request, Long commissionId, LocalDate date, RoomRequestStatus status) {
        RoomRequestItem item = RoomRequestItem.builder()
                .commissionId(commissionId)
                .date(date)
                .startTime(LocalTime.of(10, 0))
                .duration(Duration.ofMinutes(120))
                .enrolled(30)
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
