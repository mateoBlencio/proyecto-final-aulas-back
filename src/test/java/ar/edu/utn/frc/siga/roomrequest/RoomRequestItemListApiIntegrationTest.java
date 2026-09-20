package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("filtra por types y, combinado, por statuses")
    void filtersByTypesAndStatuses() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();

        RoomRequest partial = testData.solicitudDeAula(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, academic.subjectId());
        testData.itemDePedido(partial, academic.commissionId(), LocalDate.now().plusDays(10), RoomRequestStatus.NEW);
        testData.itemDePedido(partial, academic.commissionId(), LocalDate.now().plusDays(20), RoomRequestStatus.IN_EVALUATION);
        roomRequestRepository.save(partial);

        RoomRequest conference = testData.solicitudDeAula(RoomRequestType.CONFERENCE, academic.subjectId());
        testData.itemDePedido(conference, null, LocalDate.now().plusDays(15), RoomRequestStatus.NEW);
        roomRequestRepository.save(conference);

        mockMvc.perform(get("/v1/room-requests/items")
                        .param("subjectId", String.valueOf(academic.subjectId()))
                        .param("types", "PARTIAL_EXAM_OFF_SCHEDULE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));

        mockMvc.perform(get("/v1/room-requests/items")
                        .param("subjectId", String.valueOf(academic.subjectId()))
                        .param("types", "PARTIAL_EXAM_OFF_SCHEDULE")
                        .param("statuses", "IN_EVALUATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("IN_EVALUATION"))
                .andExpect(jsonPath("$.content[0].request.id").value(partial.getId()));
    }

    @Test
    @DisplayName("combina types con dateFrom/dateTo")
    void combinesTypeAndDateRangeFilters() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, academic.subjectId());
        testData.itemDePedido(request, academic.commissionId(), LocalDate.now().plusDays(5), RoomRequestStatus.NEW);
        testData.itemDePedido(request, academic.commissionId(), LocalDate.now().plusDays(50), RoomRequestStatus.NEW);
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
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, academic.subjectId());
        testData.itemDePedido(request, academic.commissionId(), LocalDate.now().minusDays(5), RoomRequestStatus.CANCELLED);
        testData.itemDePedido(request, academic.commissionId(), LocalDate.now().plusDays(5), RoomRequestStatus.NEW);
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
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, academic.subjectId());
        testData.itemDePedido(request, academic.commissionId(), LocalDate.now().plusDays(1), RoomRequestStatus.NEW);
        testData.itemDePedido(request, academic.commissionId(), LocalDate.now().plusDays(2), RoomRequestStatus.NEW);
        testData.itemDePedido(request, academic.commissionId(), LocalDate.now().plusDays(3), RoomRequestStatus.NEW);
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

        // createdAt lo sella @CreationTimestamp al persistir: el orden de alta define el orden esperado.
        RoomRequest olderRequest = testData.solicitudDeAula(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, academic.subjectId());
        testData.itemDePedido(olderRequest, academic.commissionId(), LocalDate.now().plusDays(30), RoomRequestStatus.NEW);
        roomRequestRepository.saveAndFlush(olderRequest);

        RoomRequest newerRequest = testData.solicitudDeAula(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, academic.subjectId());
        testData.itemDePedido(newerRequest, academic.commissionId(), LocalDate.now().plusDays(31), RoomRequestStatus.NEW);
        roomRequestRepository.saveAndFlush(newerRequest);

        mockMvc.perform(get("/v1/room-requests/items")
                        .param("subjectId", String.valueOf(academic.subjectId()))
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].request.id").value(newerRequest.getId()))
                .andExpect(jsonPath("$.content[1].request.id").value(olderRequest.getId()));
    }

    @Test
    @DisplayName("requiresSpecialAssignment=true trae solo pedidos con computadoras/software/examen con usuarios")
    void filtersByRequiresSpecialAssignment() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, academic.subjectId());
        RoomRequestItem plain = testData.itemDePedido(request, academic.commissionId(), LocalDate.now().plusDays(10),
                RoomRequestStatus.NEW);
        RoomRequestItem withComputers = RoomRequestItem.builder()
                .commissionId(academic.commissionId())
                .date(LocalDate.now().plusDays(11))
                .startTime(LocalTime.of(10, 0))
                .duration(Duration.ofMinutes(120))
                .estimated(35)
                .classroomCount(1)
                .requiresComputers(true)
                .build();
        request.addItem(withComputers);
        roomRequestRepository.save(request);

        mockMvc.perform(get("/v1/room-requests/items")
                        .param("subjectId", String.valueOf(academic.subjectId()))
                        .param("requiresSpecialAssignment", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].itemId").value(withComputers.getId()))
                .andExpect(jsonPath("$.content[0].requiresSpecialAssignment").value(true));

        mockMvc.perform(get("/v1/room-requests/items")
                        .param("subjectId", String.valueOf(academic.subjectId()))
                        .param("requiresSpecialAssignment", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].itemId").value(plain.getId()));
    }

    @Test
    @DisplayName("derivedBuildingId filtra por el edificio al que quedó derivado")
    void filtersByDerivedBuildingId() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        Building building = testData.edificio();
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, academic.subjectId());
        RoomRequestItem derived = testData.itemDePedido(request, academic.commissionId(), LocalDate.now().plusDays(12),
                RoomRequestStatus.NEW);
        derived.deriveTo(building.getId(), "subsecretaria@frc.utn.edu.ar", LocalDateTime.now());
        testData.itemDePedido(request, academic.commissionId(), LocalDate.now().plusDays(13), RoomRequestStatus.NEW);
        roomRequestRepository.save(request);

        mockMvc.perform(get("/v1/room-requests/items")
                        .param("subjectId", String.valueOf(academic.subjectId()))
                        .param("derivedBuildingId", String.valueOf(building.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].itemId").value(derived.getId()))
                .andExpect(jsonPath("$.content[0].derivedBuildingId").value(building.getId()))
                .andExpect(jsonPath("$.content[0].derivedBuildingName").value(building.getName()));
    }

    @Test
    @DisplayName("wasReturned=true trae solo pedidos que fueron devueltos por un edificio")
    void filtersByWasReturned() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        Building building = testData.edificio();
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, academic.subjectId());
        RoomRequestItem returned = testData.itemDePedido(request, academic.commissionId(), LocalDate.now().plusDays(14),
                RoomRequestStatus.NEW);
        returned.deriveTo(building.getId(), "subsecretaria@frc.utn.edu.ar", LocalDateTime.now());
        returned.returnFromBuilding("no había proyector");
        testData.itemDePedido(request, academic.commissionId(), LocalDate.now().plusDays(15), RoomRequestStatus.NEW);
        roomRequestRepository.save(request);

        mockMvc.perform(get("/v1/room-requests/items")
                        .param("subjectId", String.valueOf(academic.subjectId()))
                        .param("wasReturned", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].itemId").value(returned.getId()))
                .andExpect(jsonPath("$.content[0].wasReturned").value(true));
    }

    @Test
    @DisplayName("assignedClassroomCount y partiallyResolved reflejan una resolución parcial real")
    void computesAssignedClassroomCountAndPartiallyResolved() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        Building building = testData.edificio();
        Classroom aula = testData.aula(building);
        RoomRequest request = RoomRequest.builder()
                .type(RoomRequestType.FINAL_EXAM)
                .scope(AcademicScope.GRADO)
                .teacherName("Ada Lovelace")
                .teacherEmail("ada@frc.utn.edu.ar")
                .teacherPhone("351-1234567")
                .subjectId(academic.subjectId())
                .build();
        RoomRequestItem item = RoomRequestItem.builder()
                .date(LocalDate.now().plusDays(16))
                .startTime(LocalTime.of(9, 0))
                .duration(Duration.ofMinutes(90))
                .estimated(30)
                .classroomCount(2)
                .build();
        request.addItem(item);
        roomRequestRepository.save(request);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "classroomIds", List.of(aula.getId()),
                                "reason", "solo hay una disponible"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/room-requests/items")
                        .param("subjectId", String.valueOf(academic.subjectId()))
                        .param("includePast", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].assignedClassroomCount").value(1))
                .andExpect(jsonPath("$.content[0].partiallyResolved").value(true));

        mockMvc.perform(get("/v1/room-requests/items")
                        .param("subjectId", String.valueOf(academic.subjectId()))
                        .param("partiallyResolved", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @DisplayName("sort=derivedAt y sort=decidedAt están en la whitelist")
    void sortsByDerivedAtAndDecidedAt() throws Exception {
        mockMvc.perform(get("/v1/room-requests/items").param("sort", "derivedAt,desc"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/room-requests/items").param("sort", "decidedAt,asc"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("sin token: 401; con AUXILIAR_AULICO: 200 (lectura habilitada para ambos roles)")
    void authenticationAndAuthorization() throws Exception {
        MockMvc anonymousMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        anonymousMockMvc.perform(get("/v1/room-requests/items"))
                .andExpect(status().isUnauthorized());

        mockMvcAs("auxiliar@frc.utn.edu.ar", SystemRole.AUXILIAR_AULICO)
                .perform(get("/v1/room-requests/items"))
                .andExpect(status().isOk());
    }
}
