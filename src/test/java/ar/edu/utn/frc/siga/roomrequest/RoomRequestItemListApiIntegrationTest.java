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
 * Cada test escala su búsqueda por el {@code subjectId} único que arma
 * {@link IntegrationTestData#materiaYComision()}: la base no se limpia entre tests, así que sin
 * ese scope los totales de otros tests contaminarían las aserciones.
 */
@Import(IntegrationTestData.class)
@DisplayName("GET /v1/room-requests/items (integración)")
class RoomRequestItemListApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private JwtService jwtService;

    @Test
    @DisplayName("filtra por types y, combinado, por statuses")
    void filtersByTypesAndStatuses() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();

        RoomRequest partial = seedRequest(RoomRequestType.PARTIAL_EXAM, academic.subjectId());
        seedItem(partial, academic.commissionId(), LocalDate.now().plusDays(10), RoomRequestStatus.PENDING);
        seedItem(partial, academic.commissionId(), LocalDate.now().plusDays(20), RoomRequestStatus.PRE_APPROVED);
        roomRequestRepository.save(partial);

        RoomRequest conference = seedRequest(RoomRequestType.CONFERENCE, academic.subjectId());
        seedItem(conference, null, LocalDate.now().plusDays(15), RoomRequestStatus.PENDING);
        roomRequestRepository.save(conference);

        mockMvc.perform(get("/v1/room-requests/items")
                        .param("subjectId", String.valueOf(academic.subjectId()))
                        .param("types", "PARTIAL_EXAM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));

        mockMvc.perform(get("/v1/room-requests/items")
                        .param("subjectId", String.valueOf(academic.subjectId()))
                        .param("types", "PARTIAL_EXAM")
                        .param("statuses", "PRE_APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PRE_APPROVED"))
                .andExpect(jsonPath("$.content[0].request.id").value(partial.getId()));
    }

    @Test
    @DisplayName("combina types con dateFrom/dateTo")
    void combinesTypeAndDateRangeFilters() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        RoomRequest request = seedRequest(RoomRequestType.PARTIAL_EXAM, academic.subjectId());
        seedItem(request, academic.commissionId(), LocalDate.now().plusDays(5), RoomRequestStatus.PENDING);
        seedItem(request, academic.commissionId(), LocalDate.now().plusDays(50), RoomRequestStatus.PENDING);
        roomRequestRepository.save(request);

        mockMvc.perform(get("/v1/room-requests/items")
                        .param("subjectId", String.valueOf(academic.subjectId()))
                        .param("dateFrom", LocalDate.now().toString())
                        .param("dateTo", LocalDate.now().plusDays(10).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].date").value(LocalDate.now().plusDays(5).toString()));
    }

    @Test
    @DisplayName("includePast=false oculta pedidos vencidos; includePast=true los trae")
    void includePastTogglesVisibilityOfPastItems() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        RoomRequest request = seedRequest(RoomRequestType.PARTIAL_EXAM, academic.subjectId());
        seedItem(request, academic.commissionId(), LocalDate.now().minusDays(5), RoomRequestStatus.CANCELLED);
        seedItem(request, academic.commissionId(), LocalDate.now().plusDays(5), RoomRequestStatus.PENDING);
        roomRequestRepository.save(request);

        mockMvc.perform(get("/v1/room-requests/items")
                        .param("subjectId", String.valueOf(academic.subjectId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));

        mockMvc.perform(get("/v1/room-requests/items")
                        .param("subjectId", String.valueOf(academic.subjectId()))
                        .param("includePast", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    @DisplayName("pagina de a 2: totalElements y totalPages reflejan el total real")
    void pagesResultsWithSizeParam() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        RoomRequest request = seedRequest(RoomRequestType.PARTIAL_EXAM, academic.subjectId());
        seedItem(request, academic.commissionId(), LocalDate.now().plusDays(1), RoomRequestStatus.PENDING);
        seedItem(request, academic.commissionId(), LocalDate.now().plusDays(2), RoomRequestStatus.PENDING);
        seedItem(request, academic.commissionId(), LocalDate.now().plusDays(3), RoomRequestStatus.PENDING);
        roomRequestRepository.save(request);

        mockMvc.perform(get("/v1/room-requests/items")
                        .param("subjectId", String.valueOf(academic.subjectId()))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    @DisplayName("sort fuera de la whitelist: 400, no 500")
    void invalidSortReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/room-requests/items").param("sort", "teacherEmail,asc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("sort=createdAt: ordena por la fecha de alta de la solicitud, no del pedido")
    void sortsByRequestCreatedAt() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();

        RoomRequest olderRequest = seedRequest(RoomRequestType.PARTIAL_EXAM, academic.subjectId(),
                LocalDateTime.now().minusDays(5));
        seedItem(olderRequest, academic.commissionId(), LocalDate.now().plusDays(30), RoomRequestStatus.PENDING);
        roomRequestRepository.save(olderRequest);

        RoomRequest newerRequest = seedRequest(RoomRequestType.PARTIAL_EXAM, academic.subjectId(),
                LocalDateTime.now());
        seedItem(newerRequest, academic.commissionId(), LocalDate.now().plusDays(31), RoomRequestStatus.PENDING);
        roomRequestRepository.save(newerRequest);

        mockMvc.perform(get("/v1/room-requests/items")
                        .param("subjectId", String.valueOf(academic.subjectId()))
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].request.id").value(newerRequest.getId()))
                .andExpect(jsonPath("$.content[1].request.id").value(olderRequest.getId()));
    }

    @Test
    @DisplayName("sin token: 401; con AUXILIAR_AULICO: 200 (lectura habilitada para ambos roles)")
    void authenticationAndAuthorization() throws Exception {
        MockMvc anonymousMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        anonymousMockMvc.perform(get("/v1/room-requests/items"))
                .andExpect(status().isUnauthorized());

        String auxToken = jwtService.generateAccessToken("auxiliar@frc.utn.edu.ar", Set.of(Role.AUXILIAR_AULICO));
        anonymousMockMvc.perform(get("/v1/room-requests/items")
                        .header("Authorization", "Bearer " + auxToken))
                .andExpect(status().isOk());
    }

    private RoomRequest seedRequest(RoomRequestType type, Long subjectId) {
        return seedRequest(type, subjectId, LocalDateTime.now());
    }

    private RoomRequest seedRequest(RoomRequestType type, Long subjectId, LocalDateTime createdAt) {
        return RoomRequest.builder()
                .type(type)
                .scope(AcademicScope.GRADO)
                .teacherName("Ada Lovelace")
                .teacherEmail("ada@frc.utn.edu.ar")
                .teacherPhone("351-1234567")
                .subjectId(subjectId)
                .createdAt(createdAt)
                .build();
    }

    /** Statuses distintos de PENDING pasan por {@code decide(...)}: el check constraint de la tabla exige decidedBy/decidedAt en ese caso. */
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
