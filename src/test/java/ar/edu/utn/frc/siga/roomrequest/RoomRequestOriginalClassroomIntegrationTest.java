package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.model.ClassroomMovements;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestExpiryService;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@RecordApplicationEvents
@DisplayName("Reabrir y cancelar vuelven al aula original (integración)")
class RoomRequestOriginalClassroomIntegrationTest extends AbstractIntegrationTest {

    private static final LocalTime START = LocalTime.of(18, 0);
    /** Período propio: dejan pedidos con ocurrencias sin aula que el recálculo por período de otros tests no puede borrar. */
    private static final int OWN_PERIOD_YEAR = 3900;

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private RoomRequestItemRepository itemRepository;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private RoomRequestExpiryService expiryService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ApplicationEvents events;

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

    private Series futureSeries(int weeks) {
        return series(firstFutureMonday(), weeks);
    }

    private void allocate(Occurrence occurrence, Classroom classroom, AllocationSource source) {
        allocationRepository.save(Allocation.builder().occurrenceId(occurrence.getId())
                .classroomId(classroom.getId()).source(source).build());
    }

    private void allocateAll(Series series, Classroom classroom, AllocationSource source) {
        series.occurrences().forEach(o -> allocate(o, classroom, source));
    }

    private Optional<Allocation> allocationOf(Occurrence occurrence) {
        return allocationRepository.findByOccurrenceId(occurrence.getId());
    }

    private Long classroomOf(Occurrence occurrence) {
        return allocationOf(occurrence).map(Allocation::getClassroomId).orElse(null);
    }

    private Classroom room() {
        return testData.aula(testData.edificio());
    }

    private RoomRequestItem regularItem(Series series) {
        return seedItem(RoomRequestType.REGULAR_ROOM_CHANGE, series, null);
    }

    private RoomRequestItem seedItem(RoomRequestType type, Series series, LocalDate date) {
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

    private ResultActions call(RoomRequestItem item, String action, String body) throws Exception {
        var request = post("/v1/room-requests/items/" + item.getId() + "/" + action);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private void assign(RoomRequestItem item, Classroom... classrooms) throws Exception {
        String ids = String.join(",", java.util.Arrays.stream(classrooms).map(c -> String.valueOf(c.getId())).toList());
        call(item, "assign", "{\"classroomIds\":[" + ids + "],\"reason\":\"motivo de prueba\"}")
                .andExpect(status().isOk());
    }

    private void notifyItem(RoomRequestItem item) throws Exception {
        call(item, "notify", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NOTIFIED"));
    }

    private ResultActions reopen(RoomRequestItem item) throws Exception {
        return call(item, "reopen", "{\"reason\":\"queda lejos\"}");
    }

    private ResultActions cancel(RoomRequestItem item) throws Exception {
        return call(item, "cancel", "{\"reason\":\"se suspendió\"}");
    }

    private RoomRequestItem reload(RoomRequestItem item) {
        return itemRepository.findById(item.getId()).orElseThrow();
    }

    private List<Long> rowClassrooms(RoomRequestItem item) {
        return jdbc.queryForList("select id_aula from solicitud_item_asignacion where id_item = ? and orden = 1 "
                + "order by id_ocurrencia", Long.class, item.getId());
    }

    private void deactivate(Classroom classroom) {
        jdbc.update("update aula set eliminado_en = now() where id_aula = ?", classroom.getId());
    }

    private Series occupantIn(Classroom classroom, LocalDate date) {
        Series occupant = series(date, 1);
        allocate(occupant.at(0), classroom, AllocationSource.MANUAL);
        return occupant;
    }

    // ---------- reopen ----------

    @Test
    @DisplayName("reopen NOTIFIED con el aula original libre: todas las fechas vuelven y la reversión lo informa")
    void reopen_originalLibre() throws Exception {
        Classroom a = room();
        Classroom b = room();
        Series series = futureSeries(3);
        allocateAll(series, a, AllocationSource.MANUAL);
        RoomRequestItem item = regularItem(series);
        assign(item, b);
        notifyItem(item);

        reopen(item).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_EVALUATION"))
                .andExpect(jsonPath("$.reversion.reverted.length()").value(3))
                .andExpect(jsonPath("$.reversion.reverted[0].classroom.id").value(a.getId()))
                .andExpect(jsonPath("$.reversion.kept.length()").value(0));

        series.occurrences().forEach(o -> assertThat(classroomOf(o)).isEqualTo(a.getId()));
        assertThat(rowClassrooms(item)).containsOnly(a.getId());
        assertThat(reload(item).getStatus()).isEqualTo(RoomRequestStatus.IN_EVALUATION);
    }

    @Test
    @DisplayName("reopen NOTIFIED con una fecha ocupada en el original: esa fecha queda en la notificada, informa quién la ocupa y las demás vuelven")
    void reopen_unaFechaOcupada() throws Exception {
        Classroom a = room();
        Classroom b = room();
        Series series = futureSeries(3);
        allocateAll(series, a, AllocationSource.MANUAL);
        RoomRequestItem item = regularItem(series);
        assign(item, b);
        occupantIn(a, series.at(1).getDate());
        notifyItem(item);

        reopen(item).andExpect(status().isOk())
                .andExpect(jsonPath("$.reversion.reverted.length()").value(2))
                .andExpect(jsonPath("$.reversion.kept.length()").value(1))
                .andExpect(jsonPath("$.reversion.kept[0].date").value(series.at(1).getDate().toString()))
                .andExpect(jsonPath("$.reversion.kept[0].originalClassroom.id").value(a.getId()))
                .andExpect(jsonPath("$.reversion.kept[0].occupiedBy.subjectName").isNotEmpty());

        assertThat(classroomOf(series.at(0))).isEqualTo(a.getId());
        assertThat(classroomOf(series.at(1))).isEqualTo(b.getId());
        assertThat(classroomOf(series.at(2))).isEqualTo(a.getId());
    }

    @Test
    @DisplayName("reopen NOTIFIED con el aula original desactivada: no se escribe, queda en la notificada y no cuenta como ocupada")
    void reopen_originalDesactivado() throws Exception {
        Classroom a = room();
        Classroom b = room();
        Series series = futureSeries(2);
        allocateAll(series, a, AllocationSource.MANUAL);
        RoomRequestItem item = regularItem(series);
        assign(item, b);
        notifyItem(item);
        deactivate(a);

        reopen(item).andExpect(status().isOk())
                .andExpect(jsonPath("$.reversion.reverted.length()").value(0))
                .andExpect(jsonPath("$.reversion.kept.length()").value(2))
                .andExpect(jsonPath("$.reversion.kept[0].occupiedBy").doesNotExist());

        series.occurrences().forEach(o -> assertThat(classroomOf(o)).isEqualTo(b.getId()));
    }

    @Test
    @DisplayName("reopen de un cambio regular con clases pasadas: las pasadas no cambian y las futuras vuelven")
    void reopen_regularConClasesPasadas() throws Exception {
        Classroom a = room();
        Classroom b = room();
        LocalDate start = LocalDate.now().minusDays(15);
        Series series = series(start, 5);
        allocateAll(series, a, AllocationSource.MANUAL);
        RoomRequestItem item = regularItem(series);
        assign(item, b);
        notifyItem(item);
        List<Long> before = series.occurrences().stream().map(this::classroomOf).toList();
        assertThat(before).contains(b.getId());

        reopen(item).andExpect(status().isOk())
                .andExpect(jsonPath("$.reversion.reverted.length()").value(2));

        for (Occurrence o : series.occurrences()) {
            boolean past = o.getDate().isBefore(LocalDate.now());
            if (past) {
                assertThat(classroomOf(o)).as("pasada " + o.getDate()).isEqualTo(before.get(series.occurrences().indexOf(o)));
            } else {
                assertThat(classroomOf(o)).as("futura " + o.getDate()).isEqualTo(a.getId());
            }
        }
    }

    @Test
    @DisplayName("reopen desde RESOLVED no cambia el calendario ni devuelve reversión")
    void reopen_desdeResueltoNoCambiaNada() throws Exception {
        Classroom a = room();
        Classroom b = room();
        Series series = futureSeries(2);
        allocateAll(series, a, AllocationSource.MANUAL);
        RoomRequestItem item = regularItem(series);
        assign(item, b);
        notifyItem(item);
        call(item, "confirm", null).andExpect(status().isOk()).andExpect(jsonPath("$.reversion").doesNotExist());

        reopen(item).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_EVALUATION"))
                .andExpect(jsonPath("$.reversion").doesNotExist());

        series.occurrences().forEach(o -> assertThat(classroomOf(o)).isEqualTo(b.getId()));
        assertThat(reload(item).getReopenedFromNotifiedOn()).isNull();
    }

    @Test
    @DisplayName("reopen de un tipo creado por SIGA (FINAL_EXAM) no toca el calendario ni devuelve reversión")
    void reopen_tipoCreadoPorSiga() throws Exception {
        Classroom b = room();
        RoomRequest request = RoomRequest.builder().type(RoomRequestType.FINAL_EXAM).scope(AcademicScope.GRADO)
                .teacherName("Ada Lovelace").teacherEmail("ada@frc.utn.edu.ar").teacherPhone("351-1234567")
                .subjectId(testData.materiaYComision(OWN_PERIOD_YEAR).subjectId()).build();
        RoomRequestItem item = RoomRequestItem.builder().date(LocalDate.now().plusDays(30)).startTime(LocalTime.of(9, 0))
                .duration(Duration.ofMinutes(90)).estimated(30).classroomCount(1).build();
        request.addItem(item);
        roomRequestRepository.save(request);
        assign(item, b);
        notifyItem(item);

        reopen(item).andExpect(status().isOk()).andExpect(jsonPath("$.reversion").doesNotExist());

        Long occurrenceId = jdbc.queryForObject(
                "select id_ocurrencia from solicitud_item_asignacion where id_item = ?", Long.class, item.getId());
        assertThat(allocationRepository.findByOccurrenceId(occurrenceId).orElseThrow().getClassroomId())
                .isEqualTo(b.getId());
    }

    @Test
    @DisplayName("el origen SYSACAD se conserva al restaurar y el movimiento se publica como MANUAL")
    void reopen_conservaOrigenSysacadYPublicaManual() throws Exception {
        Classroom a = room();
        Classroom b = room();
        Series series = futureSeries(1);
        allocateAll(series, a, AllocationSource.SYSACAD);
        RoomRequestItem item = regularItem(series);
        assign(item, b);
        assertThat(allocationOf(series.at(0)).orElseThrow().getSource()).isEqualTo(AllocationSource.MANUAL);
        notifyItem(item);
        events.clear();

        reopen(item).andExpect(status().isOk());

        Allocation restored = allocationOf(series.at(0)).orElseThrow();
        assertThat(restored.getClassroomId()).isEqualTo(a.getId());
        assertThat(restored.getSource()).isEqualTo(AllocationSource.SYSACAD);
        List<ClassroomMovements> movements = events.stream(ClassroomMovements.class).toList();
        assertThat(movements).hasSize(1);
        assertThat(movements.getFirst().source()).isEqualTo(AllocationSource.MANUAL);
        assertThat(movements.getFirst().changes()).extracting(ClassroomMovements.Change::toClassroomId)
                .containsExactly(a.getId());
    }

    @Test
    @DisplayName("pedido anterior a V60 (original_capturado=false): usa previousClassroomId con origen MANUAL")
    void reopen_pedidoPreV60() throws Exception {
        Classroom a = room();
        Classroom b = room();
        Series series = futureSeries(2);
        allocateAll(series, a, AllocationSource.SYSACAD);
        RoomRequestItem item = regularItem(series);
        assign(item, b);
        notifyItem(item);
        jdbc.update("update solicitud_item_asignacion set original_capturado = false, id_aula_original = null, "
                + "origen_original = null where id_item = ?", item.getId());

        reopen(item).andExpect(status().isOk()).andExpect(jsonPath("$.reversion.reverted.length()").value(2));

        series.occurrences().forEach(o -> {
            assertThat(classroomOf(o)).isEqualTo(a.getId());
            assertThat(allocationOf(o).orElseThrow().getSource()).isEqualTo(AllocationSource.MANUAL);
        });
    }

    @Test
    @DisplayName("una fecha que no tenía aula no se restaura a un aula ajena: reopen la deja en la notificada y cancel la libera")
    void fechaSinAulaPreviaNoSeRestauraAUnAulaAjena() throws Exception {
        Classroom a = room();
        Classroom b = room();
        Series series = futureSeries(2);
        allocate(series.at(0), a, AllocationSource.MANUAL);
        RoomRequestItem item = regularItem(series);
        assign(item, b);
        notifyItem(item);

        reopen(item).andExpect(status().isOk())
                .andExpect(jsonPath("$.reversion.reverted.length()").value(1))
                .andExpect(jsonPath("$.reversion.kept.length()").value(1))
                .andExpect(jsonPath("$.reversion.kept[0].originalClassroom").doesNotExist());
        assertThat(classroomOf(series.at(0))).isEqualTo(a.getId());
        assertThat(classroomOf(series.at(1))).isEqualTo(b.getId());

        cancel(item).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(classroomOf(series.at(0))).isEqualTo(a.getId());
        assertThat(classroomOf(series.at(1))).isNull();
    }

    @Test
    @DisplayName("PARTIAL_EXAM_IN_CLASS: assign llena previousClassroom y reopen vuelve al aula original")
    void partialExamInClass_llenaPreviousClassroomYRestaura() throws Exception {
        Classroom a = room();
        Classroom b = room();
        Series series = futureSeries(1);
        allocateAll(series, a, AllocationSource.MANUAL);
        RoomRequestItem item = seedItem(RoomRequestType.PARTIAL_EXAM_IN_CLASS, series, series.at(0).getDate());

        call(item, "assign", "{\"classroomIds\":[" + b.getId() + "],\"reason\":\"x\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.previousClassroom.id").value(a.getId()));
        notifyItem(item);
        reopen(item).andExpect(status().isOk()).andExpect(jsonPath("$.reversion.reverted.length()").value(1));

        assertThat(classroomOf(series.at(0))).isEqualTo(a.getId());
    }

    // ---------- cancel ----------

    @Test
    @DisplayName("cancel NOTIFIED con el original libre: queda CANCELLED y la clase en el aula original con su origen")
    void cancel_originalLibre() throws Exception {
        Classroom a = room();
        Classroom b = room();
        Series series = futureSeries(3);
        allocateAll(series, a, AllocationSource.SYSACAD);
        RoomRequestItem item = regularItem(series);
        assign(item, b);
        notifyItem(item);

        cancel(item).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.reversion").doesNotExist());

        series.occurrences().forEach(o -> {
            assertThat(classroomOf(o)).isEqualTo(a.getId());
            assertThat(allocationOf(o).orElseThrow().getSource()).isEqualTo(AllocationSource.SYSACAD);
        });
        assertThat(reload(item).getStatus()).isEqualTo(RoomRequestStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancel desde IN_EVALUATION con aula asignada: vuelve al aula original")
    void cancel_desdeEnEvaluacion() throws Exception {
        Classroom a = room();
        Classroom b = room();
        Series series = futureSeries(2);
        allocateAll(series, a, AllocationSource.MANUAL);
        RoomRequestItem item = regularItem(series);
        assign(item, b);

        cancel(item).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        series.occurrences().forEach(o -> assertThat(classroomOf(o)).isEqualTo(a.getId()));
    }

    @Test
    @DisplayName("cancel con una fecha ocupada en el original: 409 con la lista y no cambia nada")
    void cancel_fechaOcupada() throws Exception {
        Classroom a = room();
        Classroom b = room();
        Series series = futureSeries(3);
        allocateAll(series, a, AllocationSource.MANUAL);
        RoomRequestItem item = regularItem(series);
        assign(item, b);
        occupantIn(a, series.at(2).getDate());
        notifyItem(item);

        cancel(item).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORIGINAL_CLASSROOM_OCCUPIED"))
                .andExpect(jsonPath("$.itemId").value(item.getId()))
                .andExpect(jsonPath("$.conflicts.length()").value(1))
                .andExpect(jsonPath("$.conflicts[0].date").value(series.at(2).getDate().toString()))
                .andExpect(jsonPath("$.conflicts[0].originalClassroom.id").value(a.getId()))
                .andExpect(jsonPath("$.conflicts[0].occupiedBy.subjectName").isNotEmpty());

        assertThat(reload(item).getStatus()).isEqualTo(RoomRequestStatus.NOTIFIED);
        series.occurrences().forEach(o -> assertThat(classroomOf(o)).isEqualTo(b.getId()));
        assertThat(rowClassrooms(item)).containsOnly(b.getId());
    }

    @Test
    @DisplayName("cancel con el original desactivado en una fecha: esa fecha queda sin aula y las demás vuelven")
    void cancel_originalDesactivadoLiberaLaFecha() throws Exception {
        Classroom a = room();
        Classroom c = room();
        Classroom b = room();
        Series series = futureSeries(2);
        allocate(series.at(0), a, AllocationSource.MANUAL);
        allocate(series.at(1), c, AllocationSource.MANUAL);
        RoomRequestItem item = regularItem(series);
        assign(item, b);
        notifyItem(item);
        deactivate(c);

        cancel(item).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(classroomOf(series.at(0))).isEqualTo(a.getId());
        assertThat(classroomOf(series.at(1))).isNull();
    }

    // ---------- espejos ----------

    private List<Occurrence> mirrorsOf(Series series) {
        return occurrenceRepository.findByEvent_Id(series.eventId()).stream()
                .filter(o -> o.getMirrorOfOccurrenceId() != null).toList();
    }

    @Test
    @DisplayName("reopen conserva los espejos asignados y cancel los retira como MIRROR_RETIRED")
    void espejos_reopenLosConservaYCancelLosRetira() throws Exception {
        Classroom a = room();
        Classroom b = room();
        Classroom m = room();
        Series series = futureSeries(1);
        allocateAll(series, a, AllocationSource.MANUAL);
        RoomRequestItem item = regularItem(series);
        assign(item, b, m);
        notifyItem(item);
        List<Occurrence> mirrors = mirrorsOf(series);
        assertThat(mirrors).hasSize(1);

        reopen(item).andExpect(status().isOk());
        assertThat(classroomOf(series.at(0))).isEqualTo(a.getId());
        assertThat(classroomOf(mirrors.getFirst())).isEqualTo(m.getId());

        notifyItem(item);
        cancel(item).andExpect(status().isOk());

        assertThat(classroomOf(series.at(0))).isEqualTo(a.getId());
        assertThat(classroomOf(mirrors.getFirst())).isNull();
        Occurrence retired = occurrenceRepository.findById(mirrors.getFirst().getId()).orElseThrow();
        assertThat(retired.getStatus()).isEqualTo(OccurrenceStatus.ROOM_RELEASED);
        assertThat(retired.getReleaseReason()).isEqualTo(ReleaseReason.MIRROR_RETIRED);
    }

    @Test
    @DisplayName("cancel: un espejo asignado al aula original del principal no bloquea la restauración")
    void espejos_enElAulaOriginalDelPrincipalNoBloqueaElCancel() throws Exception {
        Classroom a = room();
        Classroom b = room();
        Series series = futureSeries(1);
        allocateAll(series, a, AllocationSource.MANUAL);
        RoomRequestItem item = regularItem(series);
        assign(item, b, a);
        notifyItem(item);

        cancel(item).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(classroomOf(series.at(0))).isEqualTo(a.getId());
        assertThat(mirrorsOf(series)).allSatisfy(mirror -> assertThat(classroomOf(mirror)).isNull());
    }

    @Test
    @DisplayName("reopen: un espejo asignado al aula original del principal no lo deja afuera de la restauración")
    void espejos_enElAulaOriginalDelPrincipalNoBloqueaElReopen() throws Exception {
        Classroom a = room();
        Classroom b = room();
        Series series = futureSeries(1);
        allocateAll(series, a, AllocationSource.MANUAL);
        RoomRequestItem item = regularItem(series);
        assign(item, b, a);
        notifyItem(item);

        reopen(item).andExpect(status().isOk()).andExpect(jsonPath("$.reversion.reverted.length()").value(1));

        assertThat(classroomOf(series.at(0))).isEqualTo(a.getId());
        assertThat(classroomOf(mirrorsOf(series).getFirst())).isEqualTo(b.getId());
        assertThat(jdbc.queryForList("select id_aula from solicitud_item_asignacion where id_item = ? order by orden",
                Long.class, item.getId())).containsExactly(a.getId(), b.getId());
    }

    @Test
    @DisplayName("reopen: si el aula original la ocupa otra clase, la fecha queda y no hay intercambio con el espejo")
    void espejos_originalOcupadaPorOtraClaseNoIntercambia() throws Exception {
        Classroom a = room();
        Classroom b = room();
        Classroom m = room();
        Series series = futureSeries(1);
        allocateAll(series, a, AllocationSource.MANUAL);
        RoomRequestItem item = regularItem(series);
        assign(item, b, m);
        occupantIn(a, series.at(0).getDate());
        notifyItem(item);

        reopen(item).andExpect(status().isOk())
                .andExpect(jsonPath("$.reversion.reverted.length()").value(0))
                .andExpect(jsonPath("$.reversion.kept.length()").value(1));

        assertThat(classroomOf(series.at(0))).isEqualTo(b.getId());
        assertThat(classroomOf(mirrorsOf(series).getFirst())).isEqualTo(m.getId());
    }

    // ---------- reversión nula en las demás acciones ----------

    @Test
    @DisplayName("assign y notify devuelven reversion nulo")
    void otrasAcciones_reversionNula() throws Exception {
        Classroom a = room();
        Classroom b = room();
        Series series = futureSeries(1);
        allocateAll(series, a, AllocationSource.MANUAL);
        RoomRequestItem item = regularItem(series);

        call(item, "assign", "{\"classroomIds\":[" + b.getId() + "],\"reason\":\"x\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.reversion").doesNotExist());
        call(item, "notify", null).andExpect(status().isOk()).andExpect(jsonPath("$.reversion").doesNotExist());
    }

    // ---------- vencimiento ----------

    private RoomRequestItem reopenedOneTime(Classroom a, Classroom b, boolean occupyOriginal) throws Exception {
        return reopenedOneTime(a, b, occupyOriginal, null);
    }

    private RoomRequestItem reopenedOneTime(Classroom a, Classroom b, boolean occupyOriginal, Classroom reassignTo)
            throws Exception {
        Series series = futureSeries(1);
        allocateAll(series, a, AllocationSource.MANUAL);
        RoomRequestItem item = seedItem(RoomRequestType.ONE_TIME_ROOM_CHANGE, series, series.at(0).getDate());
        assign(item, b);
        if (occupyOriginal) {
            occupantIn(a, series.at(0).getDate());
        }
        notifyItem(item);
        reopen(item).andExpect(status().isOk());
        if (reassignTo != null) {
            assign(item, reassignTo);
        }
        jdbc.update("update solicitud_aula_item set fecha = ? where id_item = ?", LocalDate.now().minusDays(1), item.getId());
        return item;
    }

    @Test
    @DisplayName("vencimiento: un reabierto que se quedó en el aula notificada pasa a RESOLVED con aceptación del sistema")
    void vencimiento_reabiertoQueSeQuedo() throws Exception {
        RoomRequestItem item = reopenedOneTime(room(), room(), true);
        events.clear();

        expiryService.expireOverdueItems();

        RoomRequestItem expired = reload(item);
        assertThat(expired.getStatus()).isEqualTo(RoomRequestStatus.RESOLVED);
        assertThat(expired.getAcceptedBy()).isEqualTo("SISTEMA");
    }

    @Test
    @DisplayName("vencimiento: un reabierto que volvió al aula original pasa a CANCELLED")
    void vencimiento_reabiertoQueVolvio() throws Exception {
        RoomRequestItem item = reopenedOneTime(room(), room(), false);

        expiryService.expireOverdueItems();

        assertThat(reload(item).getStatus()).isEqualTo(RoomRequestStatus.CANCELLED);
    }

    @Test
    @DisplayName("vencimiento: un reabierto y reasignado sin renotificar pasa a CANCELLED")
    void vencimiento_reasignadoSinRenotificar() throws Exception {
        Classroom c = room();
        RoomRequestItem item = reopenedOneTime(room(), room(), true, c);
        assertThat(reload(item).getReopenedFromNotifiedOn()).isNull();

        expiryService.expireOverdueItems();

        assertThat(reload(item).getStatus()).isEqualTo(RoomRequestStatus.CANCELLED);
    }

    // ---------- concurrencia ----------

    @Test
    @DisplayName("reabrir mientras otro pedido toma el aula original: nunca 500 y el aula no queda con dos clases")
    void concurrencia_conflictoSeTrataComoOcupada() throws Exception {
        Classroom a = room();
        Classroom b = room();
        Series first = futureSeries(1);
        allocateAll(first, a, AllocationSource.MANUAL);
        RoomRequestItem item = regularItem(first);
        assign(item, b);
        notifyItem(item);
        Series other = series(first.at(0).getDate(), 1);
        RoomRequestItem otherItem = regularItem(other);
        String takeA = "{\"classroomIds\":[" + a.getId() + "],\"reason\":\"x\"}";

        List<Callable<Integer>> tasks = List.of(
                () -> reopen(item).andReturn().getResponse().getStatus(),
                () -> call(otherItem, "assign", takeA).andReturn().getResponse().getStatus());
        List<Integer> statuses = new java.util.ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (Future<Integer> f : pool.invokeAll(tasks, 60, TimeUnit.SECONDS)) {
                statuses.add(f.get());
            }
        }

        assertThat(statuses.getFirst()).isEqualTo(200);
        assertThat(statuses.get(1)).isIn(200, 409);
        boolean firstInA = a.getId().equals(classroomOf(first.at(0)));
        boolean otherInA = a.getId().equals(classroomOf(other.at(0)));
        assertThat(firstInA && otherInA).as("las dos clases quedaron en la misma aula").isFalse();
    }
}
