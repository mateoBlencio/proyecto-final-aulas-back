package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ResourceValueKind;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("GET /v1/room-requests/items/{id}/allowed-classrooms (integración)")
class RoomRequestItemAllowedClassroomsApiIntegrationTest extends AbstractIntegrationTest {

    private static final LocalTime START = LocalTime.of(18, 0);
    private static final int OWN_PERIOD_YEAR = 3900;

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private RoomRequestItemRepository roomRequestItemRepository;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private AllocationRepository allocationRepository;

    @Test
    @DisplayName("aula activa disponible: aparece con available=true")
    void findAllowedClassrooms_returnsAvailableClassroom() throws Exception {
        Building building = testData.edificio();
        Classroom classroom = testData.aula(building);
        RoomRequestItem item = seedItem(null, false, null);

        mockMvc.perform(get("/v1/room-requests/items/" + item.getId() + "/allowed-classrooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + classroom.getId() + ")].available").value(true))
                .andExpect(jsonPath("$[?(@.id == " + classroom.getId() + ")].current").value(false));
    }

    @Test
    @DisplayName("cambio regular: marca current solo el aula donde cursa la comisión, y sigue disponible")
    void findAllowedClassrooms_regular_marcaAulaActual() throws Exception {
        Building building = testData.edificio();
        Classroom actual = testData.aula(building);
        Classroom otra = testData.aula(building);
        LocalDate start = firstFutureMonday();
        Series series = series(start, 2);
        series.occurrences().forEach(o -> allocate(o, actual));
        RoomRequestItem item = seedRecurringItem(RoomRequestType.REGULAR_ROOM_CHANGE, series, null);

        mockMvc.perform(get("/v1/room-requests/items/" + item.getId() + "/allowed-classrooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + actual.getId() + ")].current").value(true))
                .andExpect(jsonPath("$[?(@.id == " + actual.getId() + ")].available").value(true))
                .andExpect(jsonPath("$[?(@.id == " + otra.getId() + ")].current").value(false));
    }

    @Test
    @DisplayName("cambio puntual: marca current el aula de esa fecha; otra clase en la misma franja sigue dejando un aula no disponible")
    void findAllowedClassrooms_puntual_marcaAulaActual() throws Exception {
        Building building = testData.edificio();
        Classroom actual = testData.aula(building);
        Classroom ocupadaPorOtro = testData.aula(building);
        LocalDate date = firstFutureMonday();
        Series series = series(date, 1);
        allocate(series.at(0), actual);
        allocate(series(date, 1).at(0), ocupadaPorOtro);
        RoomRequestItem item = seedRecurringItem(RoomRequestType.ONE_TIME_ROOM_CHANGE, series, date);

        mockMvc.perform(get("/v1/room-requests/items/" + item.getId() + "/allowed-classrooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + actual.getId() + ")].current").value(true))
                .andExpect(jsonPath("$[?(@.id == " + actual.getId() + ")].available").value(true))
                .andExpect(jsonPath("$[?(@.id == " + ocupadaPorOtro.getId() + ")].current").value(false))
                .andExpect(jsonPath("$[?(@.id == " + ocupadaPorOtro.getId() + ")].available").value(false));
    }

    @Test
    @DisplayName("requiresComputers: trae los labs con al menos 1 PC aunque ninguno solo cubra el pedido, y no las aulas sin PCs")
    void findAllowedClassrooms_requiresComputers_traeLosLabsConPcs() throws Exception {
        Building building = testData.edificio();
        Classroom lab = testData.aula(building);
        Classroom sinPcs = testData.aula(building);
        testData.recursoDeAula(lab, testData.tipoRecurso("Cantidad de PC", ResourceValueKind.COUNT), 35);
        RoomRequestItem item = seedItem(true, false, 999_999);

        mockMvc.perform(get("/v1/room-requests/items/" + item.getId() + "/allowed-classrooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.id == " + lab.getId() + ")].computerCount").value(35))
                .andExpect(jsonPath("$[?(@.id == " + sinPcs.getId() + ")]").isEmpty());
    }

    @Test
    @DisplayName("id inexistente: 404")
    void findAllowedClassrooms_idInexistente_returnsNotFound() throws Exception {
        long unknownId = 999_999_000L + IntegrationTestData.nextSeq();

        mockMvc.perform(get("/v1/room-requests/items/" + unknownId + "/allowed-classrooms"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sin token: 401")
    void sinToken_returnsUnauthorized() throws Exception {
        RoomRequestItem item = seedItem(null, false, null);

        MockMvc anonymousMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        anonymousMockMvc.perform(get("/v1/room-requests/items/" + item.getId() + "/allowed-classrooms"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AUXILIAR_AULICO sobre un ítem sin derivar: 403")
    void auxiliar_itemSinDerivar_returnsForbidden() throws Exception {
        RoomRequestItem item = seedItem(null, false, null);

        mockMvcAsAuxiliar("auxiliar-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar")
                .perform(get("/v1/room-requests/items/" + item.getId() + "/allowed-classrooms"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AUXILIAR_AULICO de otro edificio sobre un ítem derivado: 403")
    void auxiliar_itemDerivadoAOtroEdificio_returnsForbidden() throws Exception {
        Building derivedTo = testData.edificio();
        RoomRequestItem item = seedItem(null, false, null);
        derive(item, derivedTo);

        mockMvcAsAuxiliar("auxiliar-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar")
                .perform(get("/v1/room-requests/items/" + item.getId() + "/allowed-classrooms"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AUXILIAR_AULICO del edificio al que se derivó el ítem: 200")
    void auxiliar_itemDerivadoASuEdificio_returnsOk() throws Exception {
        Building derivedTo = testData.edificio();
        RoomRequestItem item = seedItem(null, false, null);
        derive(item, derivedTo);

        mockMvcAsScoped("auxiliar-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar",
                SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, derivedTo.getId())
                .perform(get("/v1/room-requests/items/" + item.getId() + "/allowed-classrooms"))
                .andExpect(status().isOk());
    }

    private record Series(Long eventId, IntegrationTestData.SubjectAndCommission academic, List<Occurrence> occurrences) {
        Occurrence at(int index) {
            return occurrences.get(index);
        }
    }

    private LocalDate firstFutureMonday() {
        return LocalDate.now().plusDays(10).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
                .plusWeeks(IntegrationTestData.nextSeq() % 200);
    }

    private Series series(LocalDate start, int weeks) {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision(OWN_PERIOD_YEAR);
        Long eventId = testData.eventoRecurrente(academic.subjectId(), academic.commissionId(), start.getDayOfWeek(),
                START, 90, start, start.plusWeeks(weeks - 1), 30);
        List<Occurrence> occurrences = occurrenceRepository.findByEvent_Id(eventId).stream()
                .sorted(Comparator.comparing(Occurrence::getDate)).toList();
        assertThat(occurrences).hasSize(weeks);
        return new Series(eventId, academic, occurrences);
    }

    private void allocate(Occurrence occurrence, Classroom classroom) {
        allocationRepository.save(Allocation.builder().occurrenceId(occurrence.getId())
                .classroomId(classroom.getId()).source(AllocationSource.MANUAL).build());
    }

    private RoomRequestItem seedRecurringItem(RoomRequestType type, Series series, LocalDate date) {
        RoomRequest request = RoomRequest.builder()
                .type(type)
                .scope(AcademicScope.GRADO)
                .teacherName("Ada Lovelace")
                .teacherEmail("ada@frc.utn.edu.ar")
                .teacherPhone("351-1234567")
                .subjectId(series.academic().subjectId())
                .build();
        RoomRequestItem item = RoomRequestItem.builder()
                .commissionId(series.academic().commissionId())
                .sourceRecurringEventId(series.eventId())
                .date(date)
                .dayOfWeek(date == null ? series.at(0).getDate().getDayOfWeek() : null)
                .startTime(START)
                .duration(Duration.ofMinutes(90))
                .classroomCount(1)
                .build();
        request.addItem(item);
        roomRequestRepository.save(request);
        return item;
    }

    private void derive(RoomRequestItem item, Building building) {
        RoomRequestItem stored = roomRequestItemRepository.findById(item.getId()).orElseThrow();
        stored.deriveTo(building.getId(), "subsecretaria@frc.utn.edu.ar", Instant.now());
        roomRequestItemRepository.save(stored);
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
