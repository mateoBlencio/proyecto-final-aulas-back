package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.RoleAssignment;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.RoleAssignmentRepository;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El mail ROOM_REQUEST_RESOLVED lleva CC al usuario que ejecuta notify y CCO a las subsecretarias habilitadas
 * menos ese usuario. Se verifica en la fila de la tabla notificacion, que es lo que el dispatcher manda después.
 */
@Import(IntegrationTestData.class)
@DisplayName("POST /v1/room-requests/items/{id}/notify: CC y CCO del mail (integración)")
class RoomRequestItemNotifyCopiesIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMINISTRADOR = "subsecretaria@frc.utn.edu.ar";
    private static final String ADMINISTRADOR_FIXTURE = "integration-test@frc.utn.edu.ar";

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private AcademicEventService academicEventService;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleAssignmentRepository roleAssignmentRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("notifica un auxiliar: CC = el auxiliar, CCO = las subsecretarias habilitadas, y Reply-To sigue siendo el del edificio")
    void notifyComoAuxiliar_ccElAuxiliarYCcoLasSubsecretarias() throws Exception {
        Building building = testData.edificio();
        String auxiliar = "auxiliar-copias-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar";
        MockMvc auxiliarMvc = mockMvcAsScoped(auxiliar, SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING,
                building.getId());
        String otroAdministrador = enabledAdministrador();
        String administradorDeshabilitado = disabledAdministrador();
        RoomRequestItem item = seedInEvaluation(building);

        auxiliarMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/notify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOTIFIED"));

        Map<String, Object> row = notificationRow(item.getId());
        assertThat(row.get("cc")).isEqualTo(auxiliar);
        assertThat(addresses(row.get("cco")))
                .contains(ADMINISTRADOR_FIXTURE, otroAdministrador)
                .doesNotContain(auxiliar, administradorDeshabilitado, "ada@frc.utn.edu.ar")
                .doesNotHaveDuplicates();
        assertThat(addresses(row.get("responder_a"))).contains(auxiliar);
        assertThat(row.get("destinatario")).isEqualTo("ada@frc.utn.edu.ar");
    }

    @Test
    @DisplayName("notifica una subsecretaria: CC = ella, CCO = las demás subsecretarias sin repetirla")
    void notifyComoAdministrador_ccoSinElActor() throws Exception {
        String otroAdministrador = enabledAdministrador();
        RoomRequestItem item = seedInEvaluation(null);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/notify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOTIFIED"));

        Map<String, Object> row = notificationRow(item.getId());
        assertThat(row.get("cc")).isEqualTo(ADMINISTRADOR_FIXTURE);
        assertThat(addresses(row.get("cco")))
                .contains(otroAdministrador)
                .doesNotContain(ADMINISTRADOR_FIXTURE)
                .doesNotHaveDuplicates();
    }

    private static List<String> addresses(Object column) {
        return column == null ? List.of() : Arrays.asList(((String) column).split(","));
    }

    private Map<String, Object> notificationRow(Long itemId) {
        return jdbcTemplate.queryForMap(
                "SELECT destinatario, responder_a, cc, cco FROM notificacion WHERE clave_idempotencia = ?",
                "room-request-item:" + itemId + ":NOTIFIED:1");
    }

    /** Crea una subsecretaria habilitada vía el helper de MockMvc y devuelve su email. */
    private String enabledAdministrador() {
        String email = "subsecretaria-extra-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar";
        mockMvcAs(email, SystemRole.ADMINISTRADOR);
        return email;
    }

    private String disabledAdministrador() {
        String email = "subsecretaria-baja-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar";
        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash("sin-uso")
                .enabled(false)
                .firstName("Baja")
                .lastName("Subsecretaria")
                .build());
        roleAssignmentRepository.save(RoleAssignment.builder()
                .user(user).role(SystemRole.ADMINISTRADOR).scopeType(ScopeType.GLOBAL).scopeId(null).build());
        return email;
    }

    /** Item de FINAL_EXAM IN_EVALUATION con una aula asignada, derivado a {@code building} si no es nulo. */
    private RoomRequestItem seedInEvaluation(Building building) {
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
                .date(LocalDate.now().plusDays(30))
                .startTime(LocalTime.of(9, 0))
                .duration(Duration.ofMinutes(90))
                .estimated(30)
                .classroomCount(1)
                .build();
        item.assignClassrooms(List.of(classroomId), List.of(List.of(occurrenceId)), List.of(), Map.of());
        request.addItem(item);
        Instant now = Instant.now();
        if (building != null) {
            item.deriveTo(building.getId(), ADMINISTRADOR, now);
        }
        item.decide(RoomRequestStatus.IN_EVALUATION, ADMINISTRADOR, "motivo de prueba", now);
        roomRequestRepository.save(request);
        return item;
    }
}
