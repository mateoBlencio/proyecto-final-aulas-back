package ar.edu.utn.frc.siga.allocation;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import ar.edu.utn.frc.siga.events.model.AcademicEvent;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("Búsqueda de asignaciones en el período (integración)")
class AllocationSearchApiIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger NEXT_YEAR = new AtomicInteger(2600);

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private SpecialtyRepository specialtyRepository;
    @Autowired
    private AcademicPeriodRepository academicPeriodRepository;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private AcademicEventRepository eventRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private record Seed(Long subjectId, Long commissionId) {}

    private record Placed(Long eventId, List<Long> occurrenceIds, Classroom classroom) {}

    private AcademicPeriod newPeriod() {
        return testData.periodoAcademico(NEXT_YEAR.getAndIncrement(), TermType.ANUAL);
    }

    private static LocalDate firstMondayOfApril(AcademicPeriod period) {
        return LocalDate.of(period.getYear(), 4, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    private Seed seed(AcademicPeriod period, String subjectName, String specialtyName, String courseCode) {
        Specialty specialty = specialtyRepository.save(Specialty.builder()
                .specialtyCode((int) IntegrationTestData.nextSeq()).name(specialtyName).build());
        StudyPlan plan = testData.planDeEstudio((int) IntegrationTestData.nextSeq(), specialty);
        Subject subject = testData.materia((int) IntegrationTestData.nextSeq(), subjectName, plan,
                TermType.ANUAL.getLabel());
        Commission commission = testData.comision(courseCode, period);
        testData.materiaComision(subject, commission, 30);
        return new Seed(subject.getId(), commission.getId());
    }

    private Seed plainSeed(AcademicPeriod period, String subjectName) {
        long id = IntegrationTestData.nextSeq();
        return seed(period, subjectName, "Spec" + id + "x", "Code" + id + "x");
    }

    private Placed placeWeekly(Seed seed, Classroom classroom, LocalDate firstDate, int weeks) throws Exception {
        Long eventId = testData.eventoRecurrente(seed.subjectId(), seed.commissionId(), firstDate.getDayOfWeek(),
                LocalTime.of(8, 0), 90, firstDate, firstDate.plusWeeks(weeks - 1L), 30);
        List<Long> occurrenceIds = occurrenceRepository.findByEvent_Id(eventId).stream()
                .sorted(Comparator.comparing(Occurrence::getDate)).map(Occurrence::getId).toList();
        allocate(mockMvc, occurrenceIds, classroom);
        return new Placed(eventId, occurrenceIds, classroom);
    }

    private Placed placeInNewClassroom(Seed seed, LocalDate firstDate, int weeks) throws Exception {
        return placeWeekly(seed, testData.aula(testData.edificio()), firstDate, weeks);
    }

    private void allocate(MockMvc client, List<Long> occurrenceIds, Classroom classroom) throws Exception {
        client.perform(post("/v1/allocations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"occurrenceIds\":" + occurrenceIds + ",\"classroomId\":"
                                + classroom.getId() + "}]}"))
                .andExpect(status().isCreated());
    }

    private JsonNode search(MockMvc client, String q, LocalDate date, Integer limit) throws Exception {
        var request = get("/v1/allocations/search").param("q", q).param("date", date.toString());
        if (limit != null) {
            request.param("limit", String.valueOf(limit));
        }
        String body = client.perform(request).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    private static List<Long> eventIds(JsonNode result) {
        return result.valueStream().map(node -> node.get("eventId").asLong()).toList();
    }

    private static List<Long> allocationIds(JsonNode event) {
        return event.get("occurrences").valueStream().map(node -> node.get("allocationId").asLong()).toList();
    }

    private static List<LocalDate> dates(JsonNode event) {
        return event.get("occurrences").valueStream().map(node -> LocalDate.parse(node.get("date").asString()))
                .toList();
    }

    private int badRequestStatus(String q, LocalDate date, Integer limit) throws Exception {
        var request = get("/v1/allocations/search").param("q", q).param("date", date.toString());
        if (limit != null) {
            request.param("limit", String.valueOf(limit));
        }
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("cada campo (materia, código de comisión, número de aula y especialidad) encuentra solo su evento")
    void matchesEachSearchableField() throws Exception {
        AcademicPeriod period = newPeriod();
        LocalDate day = firstMondayOfApril(period);
        long tag = IntegrationTestData.nextSeq();
        Seed bySubject = seed(period, "Subj" + tag + "x", "SpecA" + tag + "x", "CodeA" + tag + "x");
        Seed byCode = seed(period, "SubjB" + tag + "x", "SpecB" + tag + "x", "CodeB" + tag + "x");
        Seed byRoom = seed(period, "SubjC" + tag + "x", "SpecC" + tag + "x", "CodeC" + tag + "x");
        Seed bySpecialty = seed(period, "SubjD" + tag + "x", "SpecD" + tag + "x", "CodeD" + tag + "x");
        int roomNumber = (int) IntegrationTestData.nextSeq();
        Building building = testData.edificio();
        Placed subjectEvent = placeInNewClassroom(bySubject, day, 1);
        Placed codeEvent = placeInNewClassroom(byCode, day, 1);
        Placed roomEvent = placeWeekly(byRoom, testData.aulaConNumero(roomNumber, building,
                testData.tipoAulaPorDefecto(), 40), day, 1);
        Placed specialtyEvent = placeInNewClassroom(bySpecialty, day, 1);

        assertThat(eventIds(search(mockMvc, "Subj" + tag + "x", day, null))).containsExactly(subjectEvent.eventId());
        assertThat(eventIds(search(mockMvc, "CodeB" + tag + "x", day, null))).containsExactly(codeEvent.eventId());
        assertThat(eventIds(search(mockMvc, String.valueOf(roomNumber), day, null)))
                .containsExactly(roomEvent.eventId());
        assertThat(eventIds(search(mockMvc, "SpecD" + tag + "x", day, null)))
                .containsExactly(specialtyEvent.eventId());
    }

    @Test
    @DisplayName("la búsqueda ignora mayúsculas y tildes en la materia y encuentra una subcadena")
    void ignoresCaseAndAccents() throws Exception {
        AcademicPeriod period = newPeriod();
        LocalDate day = firstMondayOfApril(period);
        Placed event = placeInNewClassroom(plainSeed(period, "Riesgos Específicos I"), day, 1);

        assertThat(eventIds(search(mockMvc, "riesgos especificos", day, null))).containsExactly(event.eventId());
        assertThat(eventIds(search(mockMvc, "ESPECÍFICOS", day, null))).containsExactly(event.eventId());
    }

    @Test
    @DisplayName("un guion bajo en q se busca literal y no como comodín")
    void underscoreIsLiteral() throws Exception {
        AcademicPeriod period = newPeriod();
        LocalDate day = firstMondayOfApril(period);
        long tag = IntegrationTestData.nextSeq();
        Placed literal = placeInNewClassroom(plainSeed(period, "Lit" + tag + "_x"), day, 1);
        placeInNewClassroom(plainSeed(period, "Lit" + tag + "Zx"), day, 1);

        assertThat(eventIds(search(mockMvc, "Lit" + tag + "_x", day, null))).containsExactly(literal.eventId());
    }

    @Test
    @DisplayName("solo vuelven las ocurrencias dentro del período aunque el evento tenga otras asignadas fuera")
    void onlyOccurrencesInsidePeriod() throws Exception {
        AcademicPeriod period = newPeriod();
        LocalDate first = firstMondayOfApril(period);
        long tag = IntegrationTestData.nextSeq();
        Placed placed = placeInNewClassroom(plainSeed(period, "Fuera" + tag + "x"), first, 3);
        AcademicEvent event = eventRepository.findById(placed.eventId()).orElseThrow();
        LocalDate beforeStart = LocalDate.of(period.getYear(), 3, 1).with(TemporalAdjusters.previous(DayOfWeek.MONDAY));
        LocalDate afterEnd = LocalDate.of(period.getYear(), 11, 30).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        for (LocalDate outside : List.of(beforeStart, afterEnd)) {
            Occurrence saved = occurrenceRepository.save(Occurrence.builder()
                    .event(event).date(outside).status(OccurrenceStatus.NEEDS_ROOM).build());
            allocate(mockMvc, List.of(saved.getId()), placed.classroom());
        }

        JsonNode result = search(mockMvc, "Fuera" + tag + "x", first, null);

        assertThat(result).hasSize(1);
        assertThat(dates(result.get(0))).containsExactlyElementsOf(
                List.of(first, first.plusWeeks(1), first.plusWeeks(2)));
    }

    @Test
    @DisplayName("una ocurrencia sin asignar y una liberada del mismo evento no vuelven; la asignada sí")
    void excludesUnallocatedAndReleasedOccurrences() throws Exception {
        AcademicPeriod period = newPeriod();
        LocalDate first = firstMondayOfApril(period);
        long tag = IntegrationTestData.nextSeq();
        Seed seed = plainSeed(period, "Parc" + tag + "x");
        Long eventId = testData.eventoRecurrente(seed.subjectId(), seed.commissionId(), first.getDayOfWeek(),
                LocalTime.of(8, 0), 90, first, first.plusWeeks(2), 30);
        List<Long> ids = occurrenceRepository.findByEvent_Id(eventId).stream()
                .sorted(Comparator.comparing(Occurrence::getDate)).map(Occurrence::getId).toList();
        Classroom classroom = testData.aula(testData.edificio());
        allocate(mockMvc, List.of(ids.get(0)), classroom);
        allocate(mockMvc, List.of(ids.get(2)), classroom);
        mockMvc.perform(post("/v1/allocations/releases").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"occurrenceIds\":[" + ids.get(2)
                                + "]}],\"reason\":\"VIRTUAL_CLASS\"}"))
                .andExpect(status().isOk());

        JsonNode result = search(mockMvc, "Parc" + tag + "x", first, null);

        assertThat(eventIds(result)).containsExactly(eventId);
        assertThat(result.get(0).get("occurrences")).hasSize(1);
        assertThat(result.get(0).get("occurrences").get(0).get("occurrenceId").asLong()).isEqualTo(ids.get(0));
    }

    @Test
    @DisplayName("un auxiliar con alcance al edificio A ve solo el evento de A; el admin ve los dos")
    void auxiliaryOnlySeesOwnBuilding() throws Exception {
        AcademicPeriod period = newPeriod();
        LocalDate day = firstMondayOfApril(period);
        long tag = IntegrationTestData.nextSeq();
        Building buildingA = testData.edificio();
        Building buildingB = testData.edificio();
        Placed inA = placeWeekly(plainSeed(period, "Comun" + tag + "x-A"), testData.aula(buildingA), day, 1);
        Placed inB = placeWeekly(plainSeed(period, "Comun" + tag + "x-B"), testData.aula(buildingB), day, 1);
        MockMvc auxiliary = mockMvcAsScoped("aux-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar",
                SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, buildingA.getId());

        assertThat(eventIds(search(auxiliary, "Comun" + tag + "x", day, null))).containsExactly(inA.eventId());
        assertThat(eventIds(search(mockMvc, "Comun" + tag + "x", day, null)))
                .containsExactlyInAnyOrder(inA.eventId(), inB.eventId());
    }

    @Test
    @DisplayName("un auxiliar con alcance al edificio A que busca un término que solo existe en B recibe lista vacía")
    void auxiliarySearchingTermOnlyInOtherBuildingGetsEmptyList() throws Exception {
        AcademicPeriod period = newPeriod();
        LocalDate day = firstMondayOfApril(period);
        long tag = IntegrationTestData.nextSeq();
        Building buildingA = testData.edificio();
        Building buildingB = testData.edificio();
        placeWeekly(plainSeed(period, "SoloA" + tag + "x"), testData.aula(buildingA), day, 1);
        Placed inB = placeWeekly(plainSeed(period, "SoloB" + tag + "x"), testData.aula(buildingB), day, 1);
        MockMvc auxiliary = mockMvcAsScoped("aux-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar",
                SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, buildingA.getId());

        assertThat(search(auxiliary, "SoloB" + tag + "x", day, null)).isEmpty();
        assertThat(eventIds(search(mockMvc, "SoloB" + tag + "x", day, null))).containsExactly(inB.eventId());
    }

    @Test
    @DisplayName("q=%% y q=__ no son comodines: con datos sin esos caracteres literales vuelve lista vacía")
    void wildcardOnlyQueriesMatchNothing() throws Exception {
        AcademicPeriod period = newPeriod();
        LocalDate day = firstMondayOfApril(period);
        long tag = IntegrationTestData.nextSeq();
        Placed event = placeInNewClassroom(plainSeed(period, "Mix" + tag + "x%y_z"), day, 1);

        assertThat(search(mockMvc, "%%", day, null)).isEmpty();
        assertThat(search(mockMvc, "__", day, null)).isEmpty();
        assertThat(eventIds(search(mockMvc, "x%y_z", day, null))).containsExactly(event.eventId());
    }

    @Test
    @DisplayName("q de 101 caracteres responde 400 y de 100 responde 200")
    void queryLengthBoundary() throws Exception {
        LocalDate day = LocalDate.of(2026, 9, 15);

        assertThat(badRequestStatus("a".repeat(101), day, null)).isEqualTo(400);
        assertThat(badRequestStatus("a".repeat(100), day, null)).isEqualTo(200);
    }

    @Test
    @DisplayName("eventos ya terminados: primero el de última fecha más reciente y, a igual fecha, por materia")
    void finishedEventsOrderedByLastDateDescThenSubject() throws Exception {
        AcademicPeriod period = newPeriod();
        LocalDate date = LocalDate.of(period.getYear(), 6, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        long tag = IntegrationTestData.nextSeq();
        Placed oldest = placeInNewClassroom(plainSeed(period, "Ord" + tag + "x-A"), date.minusWeeks(8), 1);
        Placed recentB = placeInNewClassroom(plainSeed(period, "Ord" + tag + "x-B"), date.minusWeeks(3), 1);
        Placed recentA = placeInNewClassroom(plainSeed(period, "Ord" + tag + "x-A2"), date.minusWeeks(3), 1);

        JsonNode result = search(mockMvc, "Ord" + tag + "x", date, null);

        assertThat(eventIds(result)).containsExactly(recentA.eventId(), recentB.eventId(), oldest.eventId());
    }

    @Test
    @DisplayName("con dos períodos solapados que contienen date, el rango abarca ambos y vuelven ocurrencias de los dos")
    void overlappingPeriodsSearchUnionOfRanges() throws Exception {
        int year = NEXT_YEAR.getAndIncrement();
        AcademicPeriod annual = testData.periodoAcademico(year, TermType.ANUAL);
        AcademicPeriod firstTerm = testData.periodoAcademico(year, TermType.PRIMER_CUATRIMESTRE);
        long tag = IntegrationTestData.nextSeq();
        LocalDate inFirstTermOnly = firstMondayOfApril(firstTerm);
        LocalDate inAnnualOnly = LocalDate.of(year, 10, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        Placed early = placeInNewClassroom(plainSeed(firstTerm, "Sol" + tag + "x-1c"), inFirstTermOnly, 1);
        Placed late = placeInNewClassroom(plainSeed(annual, "Sol" + tag + "x-an"), inAnnualOnly, 1);
        annual.setStartDate(LocalDate.of(year, 5, 1));
        academicPeriodRepository.save(annual);
        LocalDate date = LocalDate.of(year, 6, 15);

        JsonNode result = search(mockMvc, "Sol" + tag + "x", date, null);

        assertThat(eventIds(result)).containsExactlyInAnyOrder(early.eventId(), late.eventId());
    }

    @Test
    @DisplayName("q de un caracter, con o sin espacios de borde, y limit fuera de 1..50 responden 400")
    void invalidParametersReturn400() throws Exception {
        LocalDate day = LocalDate.of(2026, 9, 15);

        assertThat(badRequestStatus("a", day, null)).isEqualTo(400);
        assertThat(badRequestStatus(" a ", day, null)).isEqualTo(400);
        assertThat(badRequestStatus("ab", day, 51)).isEqualTo(400);
        assertThat(badRequestStatus("ab", day, 0)).isEqualTo(400);
    }

    @Test
    @DisplayName("q de 2 caracteres y limit 1 y 50 son los valores límite válidos y responden 200")
    void boundaryParametersAreAccepted() throws Exception {
        LocalDate day = LocalDate.of(2026, 9, 15);

        assertThat(badRequestStatus("ab", day, 1)).isEqualTo(200);
        assertThat(badRequestStatus("ab", day, 50)).isEqualTo(200);
    }

    @Test
    @DisplayName("sin período que contenga la fecha responde 200 con lista vacía")
    void dateOutsideAnyPeriodReturnsEmptyList() throws Exception {
        LocalDate day = LocalDate.of(2099, 1, 5);

        assertThat(search(mockMvc, "cualquiera", day, null)).isEmpty();
    }

    @Test
    @DisplayName("limit corta eventos: con 3 coincidentes y limit=2 vuelven 2 con sus 3 ocurrencias, el más próximo primero y el terminado afuera")
    void limitCutsEventsNotOccurrences() throws Exception {
        AcademicPeriod period = newPeriod();
        LocalDate date = LocalDate.of(period.getYear(), 6, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        long tag = IntegrationTestData.nextSeq();
        Placed finished = placeInNewClassroom(plainSeed(period, "Lim" + tag + "x-fin"), date.minusWeeks(10), 3);
        Placed upcomingLater = placeInNewClassroom(plainSeed(period, "Lim" + tag + "x-tarde"), date.plusWeeks(3), 3);
        Placed upcomingNearest = placeInNewClassroom(plainSeed(period, "Lim" + tag + "x-cerca"), date, 3);

        JsonNode result = search(mockMvc, "Lim" + tag + "x", date, 2);

        assertThat(eventIds(result)).containsExactly(upcomingNearest.eventId(), upcomingLater.eventId());
        assertThat(dates(result.get(0))).containsExactly(date, date.plusWeeks(1), date.plusWeeks(2));
        assertThat(dates(result.get(1))).containsExactly(date.plusWeeks(3), date.plusWeeks(4), date.plusWeeks(5));
        assertThat(eventIds(search(mockMvc, "Lim" + tag + "x", date, 3)))
                .containsExactly(upcomingNearest.eventId(), upcomingLater.eventId(), finished.eventId());
    }

    @Test
    @DisplayName("el allocationId de la búsqueda es el id que devuelve GET /v1/allocations?date= para esa ocurrencia")
    void allocationIdMatchesListByDate() throws Exception {
        AcademicPeriod period = newPeriod();
        LocalDate day = firstMondayOfApril(period);
        long tag = IntegrationTestData.nextSeq();
        Placed placed = placeInNewClassroom(plainSeed(period, "Cmp" + tag + "x"), day, 1);
        Long occurrenceId = placed.occurrenceIds().getFirst();

        JsonNode occurrence = search(mockMvc, "Cmp" + tag + "x", day, null).get(0).get("occurrences").get(0);
        JsonNode listed = objectMapper.readTree(mockMvc.perform(get("/v1/allocations").param("date", day.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long listedAllocationId = listed.valueStream()
                .filter(a -> a.get("occurrence").get("id").asLong() == occurrenceId)
                .findFirst().orElseThrow().get("id").asLong();

        assertThat(occurrence.get("occurrenceId").asLong()).isEqualTo(occurrenceId);
        assertThat(occurrence.get("allocationId").asLong()).isEqualTo(listedAllocationId);
    }
}
