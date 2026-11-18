package ar.edu.utn.frc.siga.allocation;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.common.security.SystemScope;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Eventos suspendidos por la baja de su materia: lecturas de asignaciones, validación de solapes, 422 al
 * asignar por API y excepción del sync de SysAcad. El evento suspendido (A) tiene ocurrencias semanales en
 * hoy-7, hoy, hoy+7 y hoy+14; las tres primeras asignadas al aula X, la última sin aula. La baja de la
 * materia pasa por SubjectService.deactivate (instante actual), así que A queda suspendido desde hoy.
 */
@Import(IntegrationTestData.class)
@DisplayName("Eventos suspendidos en la API de asignaciones (integración)")
class SuspendedEventAllocationApiIntegrationTest extends AbstractIntegrationTest {

    private static final LocalTime START = LocalTime.of(9, 0);
    private static final int DURATION = 90;

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private AcademicEventRepository eventRepository;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private AllocationService allocationService;
    @Autowired
    private ar.edu.utn.frc.siga.academic.service.SubjectService subjectService;
    @Autowired
    private AcademicPeriodRepository academicPeriodRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private final LocalDate today = LocalDate.now();

    /** Evento A: materia con nombre único, 4 ocurrencias (hoy-7 a hoy+14) y aula X en las tres primeras. */
    private record Suspendable(Long eventId, Long subjectId, String subjectName, List<Occurrence> occurrences,
                               Classroom roomX) {
        Occurrence at(int index) {
            return occurrences.get(index);
        }
    }

    private Suspendable seedEventA() {
        long tag = IntegrationTestData.nextSeq();
        String subjectName = "Susp" + tag + "x";
        Specialty specialty = testData.especialidad((int) IntegrationTestData.nextSeq());
        StudyPlan plan = testData.planDeEstudio((int) IntegrationTestData.nextSeq(), specialty);
        Subject subject = testData.materia((int) IntegrationTestData.nextSeq(), subjectName, plan,
                TermType.ANUAL.getLabel());
        AcademicPeriod period = testData.periodoAcademico(2100 + (int) (tag % 500), TermType.ANUAL);
        period.setStartDate(today.minusYears(5));
        period.setEndDate(today.plusYears(15));
        period = academicPeriodRepository.save(period);
        Commission commission = testData.comision("CUR-" + tag, period);
        testData.materiaComision(subject, commission, 30);

        RecurringEvent event = eventRepository.save(RecurringEvent.builder()
                .enrolled(30).startTime(START).duration(Duration.ofMinutes(DURATION))
                .dayOfWeek(today.getDayOfWeek()).startDate(today.minusWeeks(1)).endDate(today.plusWeeks(2))
                .subjectId(subject.getId()).commissionId(commission.getId()).build());
        occurrenceRepository.saveAll(event.toOccurrences());
        List<Occurrence> occurrences = occurrenceRepository.findByEvent_Id(event.getId()).stream()
                .sorted(Comparator.comparing(Occurrence::getDate)).toList();
        assertThat(occurrences).extracting(Occurrence::getDate)
                .containsExactly(today.minusWeeks(1), today, today.plusWeeks(1), today.plusWeeks(2));

        Classroom roomX = testData.aula(testData.edificio());
        for (int i = 0; i < 3; i++) {
            allocateDirect(occurrences.get(i), roomX);
        }
        return new Suspendable(event.getId(), subject.getId(), subjectName, occurrences, roomX);
    }

    private void allocateDirect(Occurrence occurrence, Classroom room) {
        allocationRepository.save(Allocation.builder()
                .occurrenceId(occurrence.getId()).classroomId(room.getId()).source(AllocationSource.MANUAL).build());
    }

    private void suspend(Suspendable a) {
        subjectService.deactivate(a.subjectId());
    }

    private void reactivate(Suspendable a) {
        subjectService.activate(a.subjectId());
    }

    private Occurrence seedOccurrenceOf(IntegrationTestData.SubjectAndCommission sc, LocalDate date) {
        Long eventId = testData.eventoRecurrente(sc.subjectId(), sc.commissionId(), date.getDayOfWeek(), START,
                DURATION, date, date, 20);
        return occurrenceRepository.findByEvent_Id(eventId).getFirst();
    }

    private ResultActions postAllocate(String itemJson) throws Exception {
        return mockMvc.perform(post("/v1/allocations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[" + itemJson + "]}"));
    }

    private ResultActions putReallocate(String itemJson) throws Exception {
        return mockMvc.perform(put("/v1/allocations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[" + itemJson + "]}"));
    }

    private static String byOccurrence(Long occurrenceId, Long classroomId) {
        return "{\"occurrenceIds\":[" + occurrenceId + "],\"classroomId\":" + classroomId + "}";
    }

    private static String byEvent(Long eventId, Long classroomId) {
        return "{\"eventId\":" + eventId + ",\"classroomId\":" + classroomId + "}";
    }

    private List<Long> eventsOnDay(LocalDate date) throws Exception {
        String body = mockMvc.perform(get("/v1/allocations").param("date", date.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).valueStream().map(n -> n.get("event").get("id").asLong()).toList();
    }

    private JsonNode search(String q, LocalDate date) throws Exception {
        String body = mockMvc.perform(get("/v1/allocations/search").param("q", q).param("date", date.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private List<Long> conflictEventIds(String types, LocalDate from, LocalDate to, String eventField)
            throws Exception {
        String body = mockMvc.perform(get("/v1/allocations/conflicts").param("types", types)
                        .param("from", from.toString()).param("to", to.toString()).param("size", "500"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("content").valueStream()
                .map(n -> n.get(eventField).get("id").asLong()).toList();
    }

    private long rowsOf(Suspendable a) {
        return allocationRepository.findByOccurrenceIdIn(a.occurrences().stream().map(Occurrence::getId).toList())
                .size();
    }

    @Test
    @DisplayName("la baja oculta las clases desde el día de la baja en listado, búsqueda y conflictos; las anteriores siguen visibles y las filas no se tocan")
    void suspendedEvent_hidesClassesFromBajaDay_keepsEarlierOnes_andKeepsRows() throws Exception {
        Suspendable a = seedEventA();
        assertThat(eventsOnDay(today.minusWeeks(1))).contains(a.eventId());
        assertThat(eventsOnDay(today.plusWeeks(1))).contains(a.eventId());
        assertThat(search(a.subjectName(), today.minusWeeks(1)).get(0).get("occurrences")).hasSize(3);
        assertThat(conflictEventIds("UNALLOCATED", today.plusDays(10), today.plusDays(20), "event"))
                .contains(a.eventId());

        suspend(a);

        assertThat(eventsOnDay(today.minusWeeks(1))).contains(a.eventId());
        assertThat(eventsOnDay(today)).doesNotContain(a.eventId());
        assertThat(eventsOnDay(today.plusWeeks(1))).doesNotContain(a.eventId());
        JsonNode found = search(a.subjectName(), today.minusWeeks(1));
        assertThat(found).hasSize(1);
        assertThat(found.get(0).get("occurrences").valueStream().map(n -> n.get("occurrenceId").asLong()).toList())
                .containsExactly(a.at(0).getId());
        assertThat(conflictEventIds("UNALLOCATED", today.plusDays(10), today.plusDays(20), "event"))
                .doesNotContain(a.eventId());
        assertThat(rowsOf(a)).isEqualTo(3);
    }

    @Test
    @DisplayName("la asignación suspendida no ocupa el aula; al reactivar todo vuelve y el choque con lo asignado mientras tanto aparece como OVERLAP")
    void suspendedAllocation_freesTheRoom_andReactivationBringsBackAnOverlap() throws Exception {
        Suspendable a = seedEventA();
        LocalDate day = today.plusWeeks(1);
        Occurrence blockedWhileVisible = seedOccurrenceOf(testData.materiaYComision(), day);
        postAllocate(byOccurrence(blockedWhileVisible.getId(), a.roomX().getId())).andExpect(status().isConflict());

        suspend(a);
        Occurrence b = seedOccurrenceOf(testData.materiaYComision(), day);
        postAllocate(byOccurrence(b.getId(), a.roomX().getId())).andExpect(status().isCreated());

        assertThat(eventsOnDay(day)).contains(b.getEvent().getId()).doesNotContain(a.eventId());
        assertThat(conflictEventIds("OVERLAP", day, day, "eventA")).doesNotContain(a.eventId());
        assertThat(conflictEventIds("OVERLAP", day, day, "eventB")).doesNotContain(a.eventId());

        reactivate(a);

        assertThat(eventsOnDay(day)).contains(a.eventId(), b.getEvent().getId());
        String body = mockMvc.perform(get("/v1/allocations/conflicts").param("types", "OVERLAP")
                        .param("from", day.toString()).param("to", day.toString()).param("size", "500"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(body).get("content").valueStream())
                .anySatisfy(n -> assertThat(List.of(n.get("eventA").get("id").asLong(), n.get("eventB").get("id").asLong()))
                        .containsExactlyInAnyOrder(a.eventId(), b.getEvent().getId()));
    }

    @Test
    @DisplayName("POST y PUT manuales sobre un evento suspendido responden 422 'Evento suspendido' y no escriben; al reactivar el POST vuelve a funcionar")
    void manualAllocateAndReallocateOnSuspendedEvent_return422_andWriteNothing() throws Exception {
        Suspendable a = seedEventA();
        Classroom roomY = testData.aula(testData.edificio());
        suspend(a);

        postAllocate(byOccurrence(a.at(3).getId(), roomY.getId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Evento suspendido"));
        postAllocate(byEvent(a.eventId(), roomY.getId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Evento suspendido"));
        putReallocate(byOccurrence(a.at(2).getId(), roomY.getId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Evento suspendido"));

        assertThat(rowsOf(a)).isEqualTo(3);
        assertThat(allocationRepository.findByOccurrenceId(a.at(3).getId())).isEmpty();
        assertThat(allocationRepository.findByOccurrenceId(a.at(2).getId()).orElseThrow().getClassroomId())
                .isEqualTo(a.roomX().getId());

        reactivate(a);

        postAllocate(byOccurrence(a.at(3).getId(), roomY.getId())).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("el sync de SysAcad no se rechaza sobre un evento suspendido: escribe la asignación de la clase pendiente")
    void sysacadSyncOnSuspendedEvent_isNotRejected_andWritesTheAllocation() {
        Suspendable a = seedEventA();
        Classroom roomY = testData.aula(testData.edificio());
        suspend(a);

        int affected = SystemScope.call(() -> allocationService.syncFromSysacad(
                List.of(new AllocationItem(new AllocationTarget.Event(a.eventId()), roomY.getId())),
                Set.of(a.eventId())));

        assertThat(affected).isPositive();
        Allocation written = allocationRepository.findByOccurrenceId(a.at(3).getId()).orElseThrow();
        assertThat(written.getClassroomId()).isEqualTo(roomY.getId());
        assertThat(written.getSource()).isEqualTo(AllocationSource.SYSACAD);
    }
}
