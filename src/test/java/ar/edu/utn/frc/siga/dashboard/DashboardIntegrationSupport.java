package ar.edu.utn.frc.siga.dashboard;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.security.JwtService;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.settings.api.SettingsReader;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData.SubjectAndCommission;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Fixtures shared by the dashboard integration tests. Subclasses must {@code @Import(IntegrationTestData.class)}.
 *
 * <p>The database is shared by the whole suite and never cleaned between tests, so tests that assert on campus
 * totals work in a week or month that no other test touches (see {@link #week(int)}) or compare against a
 * baseline read before the fixtures are created.
 */
abstract class DashboardIntegrationSupport extends AbstractIntegrationTest {

    @Autowired
    protected IntegrationTestData testData;
    @Autowired
    protected OccurrenceRepository occurrenceRepository;
    @Autowired
    protected AllocationRepository allocationRepository;
    @Autowired
    protected RoomRequestRepository roomRequestRepository;
    @Autowired
    protected SettingsReader settingsReader;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private WebApplicationContext webApplicationContext;

    private SubjectAndCommission subjectAndCommission;

    /** An event, its single occurrence and (if allocated) the allocation row. */
    protected record Placed(Long eventId, Long occurrenceId, Long allocationId) {
    }

    /**
     * Monday of a week five years ahead, {@code slot} weeks apart. Each test that checks campus totals takes its own
     * slot, so no other test has events in that week.
     */
    protected static LocalDate week(int slot) {
        return LocalDate.now().plusYears(5).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).plusWeeks(slot);
    }

    /** Start of the daily band (events.hours.start); only the end is modified by other tests. */
    protected LocalTime bandStart() {
        return settingsReader.getTime(SettingKey.EVENTS_HOURS_START);
    }

    protected long bandMinutes() {
        return Duration.between(settingsReader.getTime(SettingKey.EVENTS_HOURS_START),
                settingsReader.getTime(SettingKey.EVENTS_HOURS_END)).toMinutes();
    }

    protected SubjectAndCommission sc() {
        if (subjectAndCommission == null) {
            subjectAndCommission = testData.materiaYComision();
        }
        return subjectAndCommission;
    }

    protected Building building() {
        return testData.edificio();
    }

    protected Classroom room(Building building, int capacity) {
        return testData.aula(building, testData.tipoAulaPorDefecto(), capacity);
    }

    /** A single class (event with one occurrence) without a room. */
    protected Placed unallocated(LocalDate date, LocalTime start, int minutes, int enrolled) {
        Long eventId = testData.eventoRecurrente(sc().subjectId(), sc().commissionId(), date.getDayOfWeek(), start,
                minutes, date, date, enrolled);
        Occurrence occurrence = occurrenceRepository.findByEvent_Id(eventId).getFirst();
        return new Placed(eventId, occurrence.getId(), null);
    }

    protected Placed allocated(Classroom room, LocalDate date, LocalTime start, int minutes, int enrolled,
                               AllocationSource source) {
        Placed placed = unallocated(date, start, minutes, enrolled);
        Allocation allocation = allocationRepository.save(Allocation.builder()
                .occurrenceId(placed.occurrenceId()).classroomId(room.getId()).source(source).build());
        return new Placed(placed.eventId(), placed.occurrenceId(), allocation.getId());
    }

    protected Placed allocated(Classroom room, LocalDate date, LocalTime start, int minutes, int enrolled) {
        return allocated(room, date, start, minutes, enrolled, AllocationSource.MANUAL);
    }

    /** Marks the event as dropped from Sysacad (hash present, habilitado_sysacad = false). */
    protected void dropFromSysacad(Long eventId) {
        jdbc.update("UPDATE evento_academico SET hash_sysacad = 'h-' || id_evento_academico, habilitado_sysacad = FALSE "
                + "WHERE id_evento_academico = ?", eventId);
    }

    protected void releaseRoom(Long occurrenceId) {
        jdbc.update("UPDATE ocurrencia SET estado = 'ROOM_RELEASED' WHERE id_ocurrencia = ?", occurrenceId);
    }

    protected void deactivateBuilding(Building building) {
        jdbc.update("UPDATE edificio SET eliminado_en = now() WHERE id_edificio = ?", building.getId());
    }

    /**
     * A room request item in the given state. {@code derivedBuildingId} may be null; {@code date} null leaves the item
     * undated; {@code time} null leaves it without start time. Created now (creado_en = now).
     */
    protected Long item(RoomRequestStatus status, Long derivedBuildingId, LocalDate date, LocalTime time) {
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.ONE_TIME_ROOM_CHANGE, sc().subjectId());
        var item = testData.itemDePedido(request, sc().commissionId(), LocalDate.now().plusDays(300),
                RoomRequestStatus.NEW);
        roomRequestRepository.save(request);
        Long itemId = item.getId();
        // chk_solicitud_item_decision: every state but NEW needs decidido_por and fecha_decision in the same statement.
        String decision = status == RoomRequestStatus.NEW ? ""
                : ", decidido_por = 'test@frc.utn.edu.ar', fecha_decision = now()";
        jdbc.update("UPDATE solicitud_aula_item SET estado = ?, id_edificio_derivado = ?, fecha = ?, hora_inicio = ?"
                + decision + " WHERE id_item = ?", status.name(), derivedBuildingId, date, time, itemId);
        if (derivedBuildingId != null) {
            jdbc.update("UPDATE solicitud_aula_item SET fecha_derivacion = now() WHERE id_item = ?", itemId);
        }
        return itemId;
    }

    /** Sets when the request that owns the item was created (creado_en is a zoneless timestamp). */
    protected void createdAt(Long itemId, LocalDateTime createdAt) {
        jdbc.update("UPDATE solicitud_aula SET creado_en = ? WHERE id_solicitud = "
                + "(SELECT id_solicitud FROM solicitud_aula_item WHERE id_item = ?)", createdAt, itemId);
    }

    /** Resolves the item with notification at {@code notifiedAt}. */
    protected void resolve(Long itemId, OffsetDateTime notifiedAt) {
        jdbc.update("UPDATE solicitud_aula_item SET estado = 'RESOLVED', decidido_por = 'test@frc.utn.edu.ar', "
                + "fecha_decision = ?, fecha_notificacion = ? WHERE id_item = ?", notifiedAt, notifiedAt, itemId);
    }

    /** Cancels the item as {@code decidedBy} at {@code decidedAt} ("SISTEMA" is the expiry process). */
    protected void cancel(Long itemId, String decidedBy, OffsetDateTime decidedAt) {
        jdbc.update("UPDATE solicitud_aula_item SET estado = 'CANCELLED', decidido_por = ?, fecha_decision = ? "
                + "WHERE id_item = ?", decidedBy, decidedAt, itemId);
    }

    /** Records that the item got {@code room} for a fresh occurrence on a date no other test uses. */
    protected void assignRoom(Long itemId, Classroom room, int order) {
        Placed placed = unallocated(week(90).plusDays(order), bandStart(), 60, 10);
        jdbc.update("INSERT INTO solicitud_item_asignacion (id_item, id_ocurrencia, id_aula, orden) VALUES (?, ?, ?, ?)",
                itemId, placed.occurrenceId(), room.getId(), order);
    }

    protected MockMvc auxiliarOf(Building building) {
        return mockMvcAsScoped("aux-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar", SystemRole.AUXILIAR_AULICO,
                ScopeType.BUILDING, building.getId());
    }

    protected MockMvc consulta() {
        return mockMvcAs("consulta-dashboard@frc.utn.edu.ar", SystemRole.CONSULTA);
    }

    protected MockMvc userWithoutRoles() {
        String email = "no-roles-dashboard@frc.utn.edu.ar";
        userRepository.findByEmailAndEnabledTrue(email).orElseGet(() -> userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("integration-test-fixture-password"))
                .enabled(true)
                .firstName("No")
                .lastName("Roles")
                .build()));
        return MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .defaultRequest(get("/").header("Authorization", "Bearer " + jwtService.generateAccessToken(email)))
                .build();
    }

    protected JsonNode json(MockMvc mvc, MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected JsonNode getJson(MockMvc mvc, String path, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get(path);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return json(mvc, request);
    }

    /** The element of {@code array} whose {@code field} equals {@code id}, or null. */
    protected static JsonNode byId(JsonNode array, String field, Long id) {
        for (JsonNode node : array) {
            if (node.get(field).asLong() == id) {
                return node;
            }
        }
        return null;
    }
}
