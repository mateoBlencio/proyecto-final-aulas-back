package ar.edu.utn.frc.siga.allocation;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.security.JwtService;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("GET /v1/allocations/occurrences-board (integración)")
class OccurrenceBoardApiIntegrationTest extends AbstractIntegrationTest {

    private static final String BOARD = "/v1/allocations/occurrences-board";
    private static final LocalTime START = LocalTime.of(9, 0);
    private static final int DURATION = 60;
    private static final AtomicInteger WINDOWS = new AtomicInteger();

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private AcademicEventService academicEventService;
    @Autowired
    private OccurrenceService occurrenceService;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private record Window(LocalDate from, LocalDate to) {
        LocalDate day(int offset) {
            return from.plusDays(offset);
        }
    }

    private record Seeded(Long eventId, Long occurrenceId) {}

    /** Ventana de 10 dias propia de cada llamada: ningun otro test siembra fechas en ella. */
    private static Window freshWindow() {
        LocalDate from = LocalDate.now().plusYears(2).plusDays(100 + 10L * WINDOWS.getAndIncrement());
        return new Window(from, from.plusDays(9));
    }

    private Seeded seed(IntegrationTestData.SubjectAndCommission sc, LocalDate date, LocalTime start, int enrolled) {
        Long eventId = testData.eventoRecurrente(sc.subjectId(), sc.commissionId(), date.getDayOfWeek(),
                start, DURATION, date, date, enrolled);
        return new Seeded(eventId, occurrenceRepository.findByEvent_Id(eventId).getFirst().getId());
    }

    private Seeded seed(IntegrationTestData.SubjectAndCommission sc, LocalDate date, int enrolled) {
        return seed(sc, date, START, enrolled);
    }

    private void allocate(MockMvc mvc, Long occurrenceId, Classroom classroom, String observation) throws Exception {
        String body = "{\"items\":[{\"occurrenceIds\":[" + occurrenceId + "],\"classroomId\":" + classroom.getId()
                + "}]" + (observation == null ? "" : ",\"observation\":\"" + observation + "\"") + "}";
        mvc.perform(post("/v1/allocations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private void allocate(Long occurrenceId, Classroom classroom) throws Exception {
        allocate(mockMvc, occurrenceId, classroom, null);
    }

    private JsonNode board(MockMvc mvc, Window w, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get(BOARD)
                .param("from", w.from().toString()).param("to", w.to().toString());
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode board(Window w, String... params) throws Exception {
        return board(mockMvc, w, params);
    }

    private static JsonNode eventNode(JsonNode page, Long eventId) {
        for (JsonNode node : page.get("content")) {
            if (node.get("event").get("id").asLong() == eventId) {
                return node;
            }
        }
        return null;
    }

    private static List<Long> eventIds(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("content").forEach(n -> ids.add(n.get("event").get("id").asLong()));
        return ids;
    }

    private static List<String> issueTypes(JsonNode date) {
        List<String> types = new ArrayList<>();
        date.get("issues").forEach(i -> types.add(i.get("type").asText()));
        return types;
    }

    private static boolean isNullOrAbsent(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return child == null || child.isNull();
    }

    private MockMvc userWithoutRoles() {
        String email = "no-roles-board@frc.utn.edu.ar";
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

    private MockMvc auxiliaryOf(Building building) {
        return mockMvcAsScoped("aux-board-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar",
                SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, building.getId());
    }

    @Test
    @DisplayName("un usuario sin el permiso CONFLICT_READ recibe 403")
    void userWithoutPermissionGets403() throws Exception {
        Window w = freshWindow();

        userWithoutRoles().perform(get(BOARD).param("from", w.from().toString()).param("to", w.to().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("principal asignada y espejo sin aula: una fila con 2 slots, UNALLOCATED con 1 aula faltante y la forma content/page")
    void principalAllocatedAndMirrorWithoutRoom() throws Exception {
        Window w = freshWindow();
        Classroom room = testData.aula(testData.edificio(), testData.tipoAulaPorDefecto(), 30);
        Seeded seeded = seed(testData.materiaYComision(), w.day(1), 20);
        allocate(seeded.occurrenceId(), room);
        Long mirrorId = occurrenceService.createSimultaneous(seeded.occurrenceId(), 1).getFirst();

        JsonNode page = board(w);

        assertThat(page.get("page").get("totalElements").asInt()).isGreaterThanOrEqualTo(1);
        JsonNode event = eventNode(page, seeded.eventId());
        assertThat(event).isNotNull();
        assertThat(event.get("occurrences")).hasSize(1);
        JsonNode date = event.get("occurrences").get(0);
        assertThat(date.get("date").asText()).isEqualTo(w.day(1).toString());
        assertThat(date.get("requiredRooms").asInt()).isEqualTo(2);
        assertThat(date.get("slots")).hasSize(2);
        JsonNode first = date.get("slots").get(0);
        JsonNode second = date.get("slots").get(1);
        assertThat(first.get("occurrenceId").asLong()).isEqualTo(seeded.occurrenceId());
        assertThat(first.get("roomSlot").asInt()).isEqualTo(1);
        assertThat(first.get("allocation").get("classroomId").asLong()).isEqualTo(room.getId());
        assertThat(second.get("occurrenceId").asLong()).isEqualTo(mirrorId);
        assertThat(second.get("roomSlot").asInt()).isEqualTo(2);
        assertThat(isNullOrAbsent(second, "allocation")).isTrue();
        assertThat(date.get("issues")).hasSize(1);
        JsonNode issue = date.get("issues").get(0);
        assertThat(issue.get("type").asText()).isEqualTo("UNALLOCATED");
        assertThat(issue.get("missingRooms").asInt()).isEqualTo(1);
        assertThat(event.get("summary").get("total").asInt()).isEqualTo(1);
        assertThat(event.get("summary").get("unallocated").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("un solape tolerado creado por la API aparece como OVERLAP en los dos eventos, con el otro evento en withEvent")
    void toleratedOverlapCreatedByApiIsListed() throws Exception {
        Window w = freshWindow();
        Classroom room = testData.aula(testData.edificio(), testData.tipoAulaPorDefecto(), 100);
        Seeded a = seed(testData.materiaYComision(), w.day(1), LocalTime.of(9, 0), 20);
        Seeded b = seed(testData.materiaYComision(), w.day(1), LocalTime.of(9, 30), 20);
        allocate(a.occurrenceId(), room);
        allocate(mockMvc, b.occurrenceId(), room, "solape tolerado, autorizado para este caso");

        JsonNode page = board(w);

        JsonNode dateA = eventNode(page, a.eventId()).get("occurrences").get(0);
        JsonNode dateB = eventNode(page, b.eventId()).get("occurrences").get(0);
        assertThat(issueTypes(dateA)).containsExactly("OVERLAP");
        assertThat(issueTypes(dateB)).containsExactly("OVERLAP");
        JsonNode overlapA = dateA.get("issues").get(0);
        JsonNode overlapB = dateB.get("issues").get(0);
        assertThat(overlapA.get("withEvent").get("id").asLong()).isEqualTo(b.eventId());
        assertThat(overlapA.get("occurrenceId").asLong()).isEqualTo(a.occurrenceId());
        assertThat(overlapA.get("classroomId").asLong()).isEqualTo(room.getId());
        assertThat(overlapB.get("withEvent").get("id").asLong()).isEqualTo(a.eventId());
        assertThat(overlapB.get("occurrenceId").asLong()).isEqualTo(b.occurrenceId());
        assertThat(eventNode(page, a.eventId()).get("summary").get("overlap").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("un auxiliar con alcance de edificio no ve el slot de otro edificio ni la fecha que solo tiene slots ajenos, y ve la fecha sin aula")
    void buildingScopeHidesSlotsOfOtherBuildings() throws Exception {
        Window w = freshWindow();
        Building a = testData.edificio();
        Building b = testData.edificio();
        Classroom roomA = testData.aula(a, testData.tipoAulaPorDefecto(), 40);
        Classroom roomB = testData.aula(b, testData.tipoAulaPorDefecto(), 40);
        Seeded split = seed(testData.materiaYComision(), w.day(1), 20);
        allocate(split.occurrenceId(), roomA);
        Long mirrorId = occurrenceService.createSimultaneous(split.occurrenceId(), 1).getFirst();
        allocate(mirrorId, roomB);
        Seeded onlyInB = seed(testData.materiaYComision(), w.day(2), 20);
        allocate(onlyInB.occurrenceId(), roomB);
        Seeded withoutRoom = seed(testData.materiaYComision(), w.day(3), 20);

        JsonNode asAuxiliary = board(auxiliaryOf(a), w);
        JsonNode asSubsecretaria = board(w);

        JsonNode splitDate = eventNode(asAuxiliary, split.eventId()).get("occurrences").get(0);
        assertThat(splitDate.get("slots")).hasSize(1);
        assertThat(splitDate.get("slots").get(0).get("allocation").get("classroomId").asLong())
                .isEqualTo(roomA.getId());
        assertThat(eventNode(asAuxiliary, onlyInB.eventId())).isNull();
        JsonNode unallocatedDate = eventNode(asAuxiliary, withoutRoom.eventId()).get("occurrences").get(0);
        assertThat(issueTypes(unallocatedDate)).containsExactly("UNALLOCATED");
        assertThat(eventNode(asSubsecretaria, split.eventId()).get("occurrences").get(0).get("slots")).hasSize(2);
        assertThat(eventNode(asSubsecretaria, onlyInB.eventId())).isNotNull();
    }

    @Test
    @DisplayName("sort inexistente, 'to' anterior a 'from' y un valor de 'issues' inválido (solo o junto a uno válido) responden 400")
    void invalidParametersReturn400() throws Exception {
        Window w = freshWindow();
        String from = w.from().toString();
        String to = w.to().toString();

        mockMvc.perform(get(BOARD).param("from", from).param("to", to).param("sort", "foo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid sort property"));
        mockMvc.perform(get(BOARD).param("from", to).param("to", from))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid date range"));
        mockMvc.perform(get(BOARD).param("from", from).param("to", to).param("issues", "FOO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parámetro con tipo inválido"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("issues")));
        mockMvc.perform(get(BOARD).param("from", from).param("to", to).param("issues", "UNALLOCATED,FOO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parámetro con tipo inválido"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("issues")));
    }

    @Test
    @DisplayName("con 3 eventos que coinciden y size=2, la página 0 trae 2, la 1 trae 1 y totalElements es 3")
    void paginatesByEvent() throws Exception {
        Window w = freshWindow();
        var sc = testData.materiaYComision();
        List<Long> eventIds = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            eventIds.add(seed(sc, w.day(i), 20).eventId());
        }
        String specialty = academicEventService.findById(eventIds.getFirst()).subject().studyPlan().specialty().name();

        JsonNode page0 = board(w, "specialty", specialty, "size", "2", "page", "0");
        JsonNode page1 = board(w, "specialty", specialty, "size", "2", "page", "1");

        assertThat(page0.get("content")).hasSize(2);
        assertThat(page0.get("page").get("totalElements").asInt()).isEqualTo(3);
        assertThat(page1.get("content")).hasSize(1);
        List<Long> seen = new ArrayList<>(eventIds(page0));
        seen.addAll(eventIds(page1));
        assertThat(seen).containsExactlyInAnyOrderElementsOf(eventIds);
    }

    @Test
    @DisplayName("issues en un solo parámetro separado por comas filtra con OR; uno solo filtra por ese tipo")
    void issuesAsCommaSeparatedSingleParameter() throws Exception {
        Window w = freshWindow();
        Building building = testData.edificio();
        Classroom big = testData.aula(building, testData.tipoAulaPorDefecto(), 100);
        Classroom small = testData.aula(building, testData.tipoAulaPorDefecto(), 10);
        Seeded unallocated = seed(testData.materiaYComision(), w.day(1), 20);
        Seeded overcrowded = seed(testData.materiaYComision(), w.day(2), 50);
        allocate(overcrowded.occurrenceId(), small);
        Seeded ok = seed(testData.materiaYComision(), w.day(3), 20);
        allocate(ok.occurrenceId(), big);

        JsonNode unfiltered = board(w);
        JsonNode both = board(w, "issues", "UNALLOCATED,OVERCROWDED");
        JsonNode onlyUnallocated = board(w, "issues", "UNALLOCATED");

        assertThat(eventIds(unfiltered)).contains(unallocated.eventId(), overcrowded.eventId(), ok.eventId());
        assertThat(eventNode(unfiltered, ok.eventId()).get("occurrences").get(0).get("issues")).isEmpty();
        assertThat(eventIds(both)).contains(unallocated.eventId(), overcrowded.eventId())
                .doesNotContain(ok.eventId());
        assertThat(eventIds(onlyUnallocated)).contains(unallocated.eventId())
                .doesNotContain(overcrowded.eventId(), ok.eventId());
        JsonNode overcrowdedIssue = eventNode(both, overcrowded.eventId()).get("occurrences").get(0).get("issues").get(0);
        assertThat(overcrowdedIssue.get("type").asText()).isEqualTo("OVERCROWDED");
        assertThat(overcrowdedIssue.get("enrolled").asInt()).isEqualTo(50);
        assertThat(overcrowdedIssue.get("capacity").asInt()).isEqualTo(10);
        assertThat(overcrowdedIssue.get("overcrowdedBy").asInt()).isEqualTo(40);
    }

    @Test
    @DisplayName("liberar con POST /releases deja el slot ROOM_RELEASED con el aula anterior; revertir lo deja NEEDS_ROOM sin aula y con la misma aula anterior")
    void releasedAndRevertedSlotKeepsPreviousClassroom() throws Exception {
        Window w = freshWindow();
        Classroom room = testData.aula(testData.edificio(), testData.tipoAulaPorDefecto(), 40);
        Seeded seeded = seed(testData.materiaYComision(), w.day(1), 20);
        allocate(seeded.occurrenceId(), room);

        mockMvc.perform(post("/v1/allocations/releases").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"occurrenceIds\":[" + seeded.occurrenceId()
                                + "]}],\"reason\":\"VIRTUAL_CLASS\",\"note\":\"cursado virtual\"}"))
                .andExpect(status().isOk());
        JsonNode released = eventNode(board(w), seeded.eventId()).get("occurrences").get(0);
        JsonNode releasedFiltered = board(w, "issues", "RELEASED");

        JsonNode releasedSlot = released.get("slots").get(0);
        assertThat(releasedSlot.get("status").asText()).isEqualTo("ROOM_RELEASED");
        assertThat(releasedSlot.get("releaseReason").asText()).isEqualTo("VIRTUAL_CLASS");
        assertThat(releasedSlot.get("releaseNote").asText()).isEqualTo("cursado virtual");
        assertThat(isNullOrAbsent(releasedSlot, "allocation")).isTrue();
        assertThat(releasedSlot.get("previousClassroom").get("classroomId").asLong()).isEqualTo(room.getId());
        assertThat(released.get("requiredRooms").asInt()).isZero();
        assertThat(released.get("issues")).isEmpty();
        assertThat(eventIds(releasedFiltered)).contains(seeded.eventId());

        mockMvc.perform(delete("/v1/allocations/releases").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"occurrenceIds\":[" + seeded.occurrenceId() + "]}]}"))
                .andExpect(status().isOk());
        JsonNode reverted = eventNode(board(w), seeded.eventId()).get("occurrences").get(0);

        JsonNode revertedSlot = reverted.get("slots").get(0);
        assertThat(revertedSlot.get("status").asText()).isEqualTo("NEEDS_ROOM");
        assertThat(isNullOrAbsent(revertedSlot, "allocation")).isTrue();
        assertThat(isNullOrAbsent(revertedSlot, "releaseReason")).isTrue();
        assertThat(revertedSlot.get("previousClassroom").get("classroomId").asLong()).isEqualTo(room.getId());
        assertThat(issueTypes(reverted)).containsExactly("UNALLOCATED");
    }
}
