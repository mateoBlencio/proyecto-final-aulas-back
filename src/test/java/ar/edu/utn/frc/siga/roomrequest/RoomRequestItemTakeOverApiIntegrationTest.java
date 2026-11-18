package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestExpiryService;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("POST /v1/room-requests/items/{id}/take-over (integración)")
class RoomRequestItemTakeOverApiIntegrationTest extends AbstractIntegrationTest {

    private static final String SUBSECRETARIA = "subsecretaria@frc.utn.edu.ar";
    private static final String BODY = "{\"reason\":\"lo resuelve subsecretaría\"}";

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private RoomRequestItemRepository itemRepository;
    @Autowired
    private AcademicEventService academicEventService;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private RoomRequestExpiryService expiryService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("IN_EVALUATION derivado: se retoma sin cambiar el estado, el auxiliar del edificio deja de leerlo y subsecretaría lo notifica")
    void takeOver_enEvaluacion() throws Exception {
        Building building = testData.edificio();
        MockMvc auxiliar = auxiliarOf(building);
        RoomRequestItem item = seed(RoomRequestStatus.IN_EVALUATION, building, LocalDate.now().plusDays(30), null);
        String url = "/v1/room-requests/items/" + item.getId();
        auxiliar.perform(get(url)).andExpect(status().isOk());

        mockMvc.perform(post(url + "/take-over").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_EVALUATION"))
                .andExpect(jsonPath("$.derivedBuilding").doesNotExist())
                .andExpect(jsonPath("$.returnedFromBuilding.id").value(building.getId()))
                .andExpect(jsonPath("$.returnedReason").value("lo resuelve subsecretaría"));

        RoomRequestItem stored = itemRepository.findById(item.getId()).orElseThrow();
        assertThat(stored.getDerivedBuildingId()).isNull();
        assertThat(stored.getDerivedAt()).isNull();
        assertThat(stored.getReturnedFromBuildingId()).isEqualTo(building.getId());
        auxiliar.perform(get(url)).andExpect(status().isForbidden());
        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions.length()").value(3))
                .andExpect(jsonPath("$.allowedActions[0]").value("ASSIGN"))
                .andExpect(jsonPath("$.allowedActions[1]").value("CANCEL"))
                .andExpect(jsonPath("$.allowedActions[2]").value("NOTIFY"));
        mockMvc.perform(post(url + "/notify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOTIFIED"));
    }

    @Test
    @DisplayName("IN_EVALUATION derivado: subsecretaría puede volver a asignar el aula tras retomarlo")
    void takeOver_luegoSePuedeAsignar() throws Exception {
        Building building = testData.edificio();
        Long newClassroomId = testData.aula(testData.edificio()).getId();
        RoomRequestItem item = seed(RoomRequestStatus.IN_EVALUATION, building, LocalDate.now().plusDays(30), null);
        String url = "/v1/room-requests/items/" + item.getId();

        mockMvc.perform(post(url + "/take-over").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());

        mockMvc.perform(post(url + "/assign").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classroomIds\":[" + newClassroomId + "],\"reason\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_EVALUATION"));
    }

    @Test
    @DisplayName("NOTIFIED derivado: se retoma conservando el plazo y el job de aceptación automática lo pasa a RESOLVED")
    void takeOver_notificadoConservaPlazoYElJobLoAcepta() throws Exception {
        Building building = testData.edificio();
        Instant deadline = Instant.now().minusSeconds(3600);
        RoomRequestItem item = seed(RoomRequestStatus.NOTIFIED, building, LocalDate.now().plusDays(30), deadline);
        String url = "/v1/room-requests/items/" + item.getId();

        mockMvc.perform(post(url + "/take-over").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOTIFIED"))
                .andExpect(jsonPath("$.acceptanceDeadline").isNotEmpty());

        RoomRequestItem retomado = itemRepository.findById(item.getId()).orElseThrow();
        assertThat(retomado.getAcceptanceDeadline()).isEqualTo(item.getAcceptanceDeadline());
        assertThat(retomado.getNotificationRound()).isEqualTo(1);

        assertThat(expiryService.acceptOverdueNotifiedItems()).isGreaterThanOrEqualTo(1);
        RoomRequestItem aceptado = itemRepository.findById(item.getId()).orElseThrow();
        assertThat(aceptado.getStatus()).isEqualTo(RoomRequestStatus.RESOLVED);
        assertThat(aceptado.getAcceptedBy()).isEqualTo("SISTEMA");
    }

    @Test
    @DisplayName("RESOLVED derivado con fechas futuras: se retoma y después subsecretaría puede reabrirlo")
    void takeOver_resueltoFuturoLuegoSePuedeReabrir() throws Exception {
        Building building = testData.edificio();
        RoomRequestItem item = seed(RoomRequestStatus.RESOLVED, building, LocalDate.now().plusDays(30), null);
        String url = "/v1/room-requests/items/" + item.getId();

        mockMvc.perform(post(url + "/take-over").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.derivedBuilding").doesNotExist());

        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions.length()").value(1))
                .andExpect(jsonPath("$.allowedActions[0]").value("REOPEN"));
        mockMvc.perform(post(url + "/reopen").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"cambia el aula\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_EVALUATION"));
    }

    @Test
    @DisplayName("RESOLVED derivado con fecha pasada: 400 y el pedido sigue derivado, sin mail")
    void takeOver_resueltoConFechaPasada() throws Exception {
        Building building = testData.edificio();
        auxiliarOf(building);
        RoomRequestItem item = seed(RoomRequestStatus.RESOLVED, building, LocalDate.now().minusDays(1), null);
        String url = "/v1/room-requests/items/" + item.getId();

        mockMvc.perform(post(url + "/take-over").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid room request"));

        assertThat(itemRepository.findById(item.getId()).orElseThrow().getDerivedBuildingId())
                .isEqualTo(building.getId());
        assertThat(takenOverMails(item.getId())).isEmpty();
        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions.length()").value(0));
    }

    @Test
    @DisplayName("CANCELLED con edificio, DERIVED_TO_BUILDING y IN_EVALUATION sin derivación: 403 y sin cambios")
    void takeOver_estadosNoRetomables() throws Exception {
        Building building = testData.edificio();
        RoomRequestItem cancelado = seed(RoomRequestStatus.CANCELLED, building, LocalDate.now().plusDays(30), null);
        RoomRequestItem derivado = seed(RoomRequestStatus.DERIVED_TO_BUILDING, building, LocalDate.now().plusDays(30), null);
        RoomRequestItem sinDerivar = seed(RoomRequestStatus.IN_EVALUATION, null, LocalDate.now().plusDays(30), null);

        for (RoomRequestItem item : List.of(cancelado, derivado, sinDerivar)) {
            mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/take-over")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden());
            RoomRequestItem stored = itemRepository.findById(item.getId()).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(item.getStatus());
            assertThat(stored.getReturnedFromBuildingId()).isNull();
        }
        assertThat(itemRepository.findById(derivado.getId()).orElseThrow().getDerivedBuildingId())
                .isEqualTo(building.getId());
    }

    @Test
    @DisplayName("auxiliar del edificio derivado: 403 y el pedido sigue derivado")
    void takeOver_auxiliarRecibe403() throws Exception {
        Building building = testData.edificio();
        RoomRequestItem item = seed(RoomRequestStatus.IN_EVALUATION, building, LocalDate.now().plusDays(30), null);

        auxiliarOf(building).perform(post("/v1/room-requests/items/" + item.getId() + "/take-over")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        assertThat(itemRepository.findById(item.getId()).orElseThrow().getDerivedBuildingId())
                .isEqualTo(building.getId());
    }

    @Test
    @DisplayName("sin motivo, vacío o en blanco: 400 y el pedido sigue derivado")
    void takeOver_sinMotivo() throws Exception {
        Building building = testData.edificio();
        RoomRequestItem item = seed(RoomRequestStatus.IN_EVALUATION, building, LocalDate.now().plusDays(30), null);
        String url = "/v1/room-requests/items/" + item.getId() + "/take-over";

        for (String body : List.of("{}", "{\"reason\":\"\"}", "{\"reason\":\"   \"}")) {
            mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid room request"));
        }
        assertThat(itemRepository.findById(item.getId()).orElseThrow().getDerivedBuildingId())
                .isEqualTo(building.getId());
    }

    @Test
    @DisplayName("id inexistente: 404")
    void takeOver_idInexistente() throws Exception {
        long unknownId = 999_999_000L + IntegrationTestData.nextSeq();

        mockMvc.perform(post("/v1/room-requests/items/" + unknownId + "/take-over")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("encola un mail ROOM_REQUEST_TAKEN_OVER para los auxiliares del edificio y un segundo retomado no lo repite")
    void takeOver_mailAAuxiliares() throws Exception {
        Building building = testData.edificio();
        String auxiliarEmail = "auxiliar-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar";
        mockMvcAsScoped(auxiliarEmail, SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, building.getId());
        RoomRequestItem item = seed(RoomRequestStatus.IN_EVALUATION, building, LocalDate.now().plusDays(30), null);
        long derivedAtMillis = item.getDerivedAt().toEpochMilli();
        String url = "/v1/room-requests/items/" + item.getId() + "/take-over";

        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        List<Map<String, Object>> rows = takenOverMails(item.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("destinatario")).isEqualTo(auxiliarEmail);
        assertThat(rows.getFirst().get("plantilla")).isEqualTo("ROOM_REQUEST_TAKEN_OVER");
        assertThat(rows.getFirst().get("clave_idempotencia"))
                .isEqualTo("room-request-item:" + item.getId() + ":TAKEN_OVER:" + derivedAtMillis);
        assertThat((String) rows.getFirst().get("cuerpo")).contains("lo resuelve subsecretaría")
                .contains("Estimado/a:").contains("Ya no aparece en su bandeja.")
                .contains("Ante cualquier consulta, responda este correo.");
    }

    @Test
    @DisplayName("edificio sin auxiliares: se retoma igual y no se encola ningún mail")
    void takeOver_sinAuxiliaresNoHayMail() throws Exception {
        Building building = testData.edificio();
        RoomRequestItem item = seed(RoomRequestStatus.IN_EVALUATION, building, LocalDate.now().plusDays(30), null);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/take-over")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());

        assertThat(takenOverMails(item.getId())).isEmpty();
    }

    @Test
    @DisplayName("la auditoría registra la operación 'Retomado de solicitud de aula derivada'")
    void takeOver_quedaAuditado() throws Exception {
        Building building = testData.edificio();
        RoomRequestItem item = seed(RoomRequestStatus.IN_EVALUATION, building, LocalDate.now().plusDays(30), null);
        int before = auditedOperations();

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/take-over")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());

        assertThat(auditedOperations()).isEqualTo(before + 1);
    }

    private int auditedOperations() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT operacion_id) FROM revinfo WHERE descripcion = ?", Integer.class,
                "Retomado de solicitud de aula derivada");
    }

    private List<Map<String, Object>> takenOverMails(Long itemId) {
        return jdbcTemplate.queryForList(
                "SELECT destinatario, plantilla, cuerpo, clave_idempotencia FROM notificacion "
                        + "WHERE clave_idempotencia LIKE ?",
                "room-request-item:" + itemId + ":TAKEN_OVER:%");
    }

    private MockMvc auxiliarOf(Building building) {
        return mockMvcAsScoped("auxiliar-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar",
                SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, building.getId());
    }

    /** Item de FINAL_EXAM con una aula asignada; derivado a {@code building} si no es nulo, en el estado pedido. */
    private RoomRequestItem seed(RoomRequestStatus status, Building building, LocalDate date, Instant deadline) {
        Long classroomId = testData.aula(testData.edificio()).getId();
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        LocalDate occurrenceDate = LocalDate.now().plusDays(29);
        var eventDto = new CreateRecurringEventRequestDto(30, LocalTime.of(9, 0), 90,
                occurrenceDate.getDayOfWeek(), occurrenceDate, occurrenceDate,
                academic.subjectId(), academic.commissionId());
        Long eventId = academicEventService.createRecurringEvent(eventDto).id();
        Long occurrenceId = occurrenceRepository.findByEvent_Id(eventId).getFirst().getId();
        RoomRequest request = RoomRequest.builder()
                .type(RoomRequestType.FINAL_EXAM)
                .scope(AcademicScope.GRADO)
                .teacherName("Ada Lovelace")
                .teacherEmail("ada@frc.utn.edu.ar")
                .teacherPhone("351-1234567")
                .subjectId(academic.subjectId())
                .build();
        RoomRequestItem item = RoomRequestItem.builder()
                .date(date)
                .startTime(LocalTime.of(9, 0))
                .duration(Duration.ofMinutes(90))
                .estimated(30)
                .classroomCount(1)
                .build();
        item.assignClassrooms(List.of(classroomId), List.of(List.of(occurrenceId)), List.of(), Map.of());
        request.addItem(item);
        Instant now = Instant.now();
        if (building != null) {
            item.deriveTo(building.getId(), SUBSECRETARIA, now);
        }
        switch (status) {
            case DERIVED_TO_BUILDING -> { }
            case IN_EVALUATION, CANCELLED -> item.decide(status, SUBSECRETARIA, "motivo de prueba", now);
            case NOTIFIED -> item.notifyTeacher(SUBSECRETARIA, now, deadline == null ? now.plus(Duration.ofDays(2)) : deadline);
            case RESOLVED -> {
                item.notifyTeacher(SUBSECRETARIA, now, now.plus(Duration.ofDays(2)));
                item.accept(SUBSECRETARIA, now);
            }
            default -> throw new IllegalArgumentException(status.name());
        }
        roomRequestRepository.save(request);
        return item;
    }
}
