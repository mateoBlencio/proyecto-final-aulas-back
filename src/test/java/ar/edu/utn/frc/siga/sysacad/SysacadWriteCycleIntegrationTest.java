package ar.edu.utn.frc.siga.sysacad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.sysacad.internal.client.SysacadClient;
import ar.edu.utn.frc.siga.sysacad.internal.client.SysacadWriteClient;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.ScheduleReadResult;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.UpdateRoomCommand;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.UpdateRoomResult;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.ScheduleViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.model.ScheduleLogType;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadReviewReason;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleLogEntry;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleStatus;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleLogRepository;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleRepository;
import ar.edu.utn.frc.siga.sysacad.internal.service.impl.SysacadCatalogSnapshotFactory;
import ar.edu.utn.frc.siga.sysacad.internal.sync.RecentSendLookup;
import ar.edu.utn.frc.siga.sysacad.internal.write.PlannedSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.write.RunReport;
import ar.edu.utn.frc.siga.sysacad.internal.write.ScheduleTimes;
import ar.edu.utn.frc.siga.sysacad.internal.write.SysacadScheduleStateWriter;
import ar.edu.utn.frc.siga.sysacad.internal.write.SysacadWriteJob;
import ar.edu.utn.frc.siga.sysacad.internal.write.SysacadWritePlanner;
import ar.edu.utn.frc.siga.sysacad.internal.write.SysacadWriteRun;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData.SubjectAndCommission;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@Import(IntegrationTestData.class)
@DisplayName("Ciclo de envío de aulas a SysAcad (integración, modo ON)")
@TestPropertySource(properties = {
        "siga.sysacad.enabled=true",
        "siga.sysacad.write.mode=ON",
        "siga.sysacad.write.job-delay=PT24H",
        "siga.sysacad.write.max-requests-per-minute=60000"
})
class SysacadWriteCycleIntegrationTest extends AbstractIntegrationTest {

    private static final int YEAR = 2095;
    private static final LocalTime START = LocalTime.of(8, 0);
    private static final LocalTime END = LocalTime.of(9, 30);
    private static final ScheduleTimes TIMES = new ScheduleTimes(START, 90, END);

    @MockitoBean
    private SysacadClient sysacadClient;
    @MockitoBean
    private SysacadCatalogSnapshotFactory snapshotFactory;
    @MockitoBean
    private SysacadWriteClient writeClient;
    @MockitoBean
    private ScheduleViewFetcher viewFetcher;

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private BuildingRepository buildingRepository;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private SysacadScheduleRepository scheduleRepository;
    @Autowired
    private SysacadScheduleLogRepository logRepository;
    @Autowired
    private SysacadScheduleStateWriter stateWriter;
    @Autowired
    private SysacadWriteRun writeRun;
    @Autowired
    private SysacadWritePlanner planner;
    @Autowired
    private RecentSendLookup recentSendLookup;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanTestRows() {
        jdbc.update("DELETE FROM sysacad_horario_registro");
        jdbc.update("DELETE FROM sysacad_horario");
        jdbc.update("UPDATE sysacad_envio_estado SET ultima_corrida_inicio = NULL, ultima_corrida_fin = NULL, "
                + "ultimo_resultado = NULL, ultima_lectura_vista = NULL, caida_desde = NULL, "
                + "ultimo_aviso_caida = NULL, pausado_hasta = NULL, ultimo_error = NULL WHERE id = 1");
    }

    private record Seeded(long scheduleId, long occurrenceId, int subject, int buildingCode, Classroom known,
            Classroom target, Classroom allocated, SubjectAndCommission academic) {
    }

    private Seeded seed(boolean sendable, boolean allocateTarget) {
        int buildingCode = 10_000 + (int) (IntegrationTestData.nextSeq() % 1_000_000);
        Building building = testData.edificio();
        building.setBuildingCode(buildingCode);
        building = buildingRepository.save(building);
        Classroom known = testData.aulaConNumero(228, building, testData.tipoAulaPorDefecto(), 40);
        Classroom target = testData.aulaConNumero(229, building, testData.tipoAulaPorDefecto(), 40);
        Classroom allocated = allocateTarget ? target : known;

        SubjectAndCommission academic = testData.materiaYComision();
        LocalDate date = LocalDate.now().plusYears(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
                .plusWeeks(IntegrationTestData.nextSeq() % 500);
        Long eventId = testData.eventoRecurrente(academic.subjectId(), academic.commissionId(), date.getDayOfWeek(),
                START, 90, date, date, 30);
        jdbc.update("UPDATE evento_academico SET hash_raw = 'h-' || id_evento_academico, habilitado_sysacad = TRUE "
                + "WHERE id_evento_academico = ?", eventId);
        Occurrence occurrence = occurrenceRepository.findByEvent_Id(eventId).getFirst();
        allocationRepository.save(Allocation.builder().occurrenceId(occurrence.getId())
                .classroomId(allocated.getId()).source(AllocationSource.MANUAL).build());

        int subject = (int) IntegrationTestData.nextSeq();
        Long scheduleId = jdbc.queryForObject("INSERT INTO sysacad_horario (anio, especialidad, plan, materia, "
                + "comision, dia, cuatrimestre, curso, hora_comienzo, hora_fin, duracion, edificio_conocido, "
                + "aula_conocida, edificio_base, aula_base, id_evento_1) "
                + "VALUES (?, 1, 2008, ?, 7, 1, 1, '1R1', '08:00', '09:30', 90, ?, 228, ?, 228, ?) "
                + "RETURNING id_horario", Long.class, YEAR, subject, buildingCode, buildingCode, eventId);
        if (!sendable) {
            jdbc.update("UPDATE sysacad_horario SET enviable = FALSE, motivo_no_enviable = 'KEY_CONFLICT' "
                    + "WHERE id_horario = ?", scheduleId);
        }
        return new Seeded(scheduleId, occurrence.getId(), subject, buildingCode, known, target, allocated, academic);
    }

    private RawSchedule viewRow(Seeded seeded, int room) {
        return viewRow(seeded, room, "08:00", "09:30", 90);
    }

    private RawSchedule viewRow(Seeded seeded, int room, String start, String end, int duration) {
        return new RawSchedule("1R1", 7, room, seeded.buildingCode(), "Edificio", 1, 1, "A", "A", start, end,
                start + "-" + end, duration, 1, "Sistemas", 2008, seeded.subject(), "Materia", 0);
    }

    private void viewShows(Seeded seeded, int room) {
        when(viewFetcher.fetch()).thenReturn(List.of(viewRow(seeded, room)));
    }

    private SysacadSchedule schedule(Seeded seeded) {
        return scheduleRepository.findById(seeded.scheduleId()).orElseThrow();
    }

    private List<String> logTypes(Seeded seeded) {
        return jdbc.queryForList("SELECT tipo FROM sysacad_horario_registro WHERE id_horario = ? "
                + "ORDER BY id_registro", String.class, seeded.scheduleId());
    }

    private void markIntent(Seeded seeded, int room) {
        stateWriter.markIntent(seeded.scheduleId(), seeded.buildingCode(), room, seeded.occurrenceId(), null, TIMES,
                START, 90);
    }

    private RunReport run() {
        return writeRun.run(Instant.now());
    }

    private void forgetViewRead() {
        jdbc.update("UPDATE sysacad_envio_estado SET ultima_lectura_vista = NULL WHERE id = 1");
    }

    @Test
    @DisplayName("Caída entre el POST y el commit: SENDING con 229 y conocido 228, la lectura de 229 confirma y no dispara cambio directo")
    void crashBetweenPostAndCommitConfirmsOnNextRead() {
        Seeded seeded = seed(true, true);
        markIntent(seeded, 229);
        SysacadSchedule crashed = schedule(seeded);
        assertThat(crashed.getStatus()).isEqualTo(SysacadScheduleStatus.SENDING);
        assertThat(crashed.getSendingRoom()).isEqualTo(229);
        assertThat(crashed.getKnownRoom()).isEqualTo(228);
        viewShows(seeded, 229);

        RunReport report = run();

        assertThat(report.result()).isEqualTo(RunReport.Result.OK);
        SysacadSchedule after = schedule(seeded);
        assertThat(after.getKnownRoom()).isEqualTo(229);
        assertThat(after.getStatus()).isEqualTo(SysacadScheduleStatus.SYNCED);
        assertThat(after.getSendingRoom()).isNull();
        assertThat(after.getDifferentRoom()).isNull();
        assertThat(logTypes(seeded)).containsExactly(ScheduleLogType.SEND_INTENT.name(),
                ScheduleLogType.SEND_CONFIRMED.name());
        assertThat(jdbc.queryForObject("SELECT resultado FROM sysacad_horario_registro WHERE id_horario = ? "
                + "AND tipo = 'SEND_CONFIRMED'", String.class, seeded.scheduleId())).isEqualTo("CONFIRMED_BY_READ");
        assertThat(allocationRepository.findByOccurrenceId(seeded.occurrenceId()).orElseThrow().getSource())
                .isEqualTo(AllocationSource.MANUAL);
        verifyNoInteractions(writeClient);
    }

    @Test
    @DisplayName("Caída con el envío ya reemplazado por otro: la fila SEND_INTENT del registro evita el falso cambio directo")
    void replacedSendIsStillRecognizedByIntentRow() {
        Seeded seeded = seed(true, true);
        markIntent(seeded, 229);
        markIntent(seeded, 230);
        assertThat(schedule(seeded).getSendingRoom()).isEqualTo(230);
        viewShows(seeded, 229);

        run();
        forgetViewRead();
        run();

        SysacadSchedule after = schedule(seeded);
        assertThat(after.getKnownRoom()).isEqualTo(229);
        assertThat(after.getStatus()).isEqualTo(SysacadScheduleStatus.SYNCED);
        assertThat(logTypes(seeded)).doesNotContain(ScheduleLogType.DIRECT_CHANGE_DETECTED.name());
        assertThat(allocationRepository.findByOccurrenceId(seeded.occurrenceId()).orElseThrow().getSource())
                .isEqualTo(AllocationSource.MANUAL);
    }

    @Test
    @DisplayName("Sin SEND_INTENT para esa aula, dos lecturas seguidas de 229 sí son un cambio directo que pisa la asignación")
    void withoutIntentRowTwoSameReadingsAreDirectChange() {
        Seeded seeded = seed(true, false);
        markIntent(seeded, 230);
        viewShows(seeded, 229);

        run();
        assertThat(logTypes(seeded)).doesNotContain(ScheduleLogType.DIRECT_CHANGE_DETECTED.name());
        jdbc.update("UPDATE sysacad_horario SET diferencia_detectada_en = now() - interval '25 hours' "
                + "WHERE id_horario = ?", seeded.scheduleId());
        forgetViewRead();
        run();

        assertThat(logTypes(seeded)).contains(ScheduleLogType.DIRECT_CHANGE_DETECTED.name(),
                ScheduleLogType.SIGA_CHANGES_ANNULLED.name());
        Allocation allocation = allocationRepository.findByOccurrenceId(seeded.occurrenceId()).orElseThrow();
        assertThat(allocation.getClassroomId()).isEqualTo(seeded.target().getId());
        assertThat(allocation.getSource()).isEqualTo(AllocationSource.SYSACAD);
        assertThat(schedule(seeded).getKnownRoom()).isEqualTo(229);
    }

    @Test
    @DisplayName("Clave repetida sin envío: un horario enviable=false no aparece en el plan y no se envía")
    void notSendableScheduleIsNeitherPlannedNorSent() {
        Seeded seeded = seed(false, true);
        viewShows(seeded, 228);

        List<PlannedSchedule> plan = planner.plan(LocalDateTime.now());
        RunReport report = run();

        assertThat(plan).extracting(p -> p.schedule().getId()).doesNotContain(seeded.scheduleId());
        assertThat(report.result()).isEqualTo(RunReport.Result.OK);
        verify(writeClient, never()).updateRoom(any());
        verify(writeClient, never()).readSchedule(anyInt(), anyInt(), anyInt());
        assertThat(schedule(seeded).getKnownRoom()).isEqualTo(228);
        assertThat(logTypes(seeded)).isEmpty();
    }

    @Test
    @DisplayName("Control de la clave repetida: el mismo horario enviable se envía de punta a punta con la hora de la vista")
    void sendableScheduleIsSentEndToEnd() {
        Seeded seeded = seed(true, true);
        viewShows(seeded, 228);
        when(writeClient.readSchedule(1, 2008, seeded.subject())).thenReturn(
                new ScheduleReadResult.Rows(List.of(viewRow(seeded, 228))),
                new ScheduleReadResult.Rows(List.of(viewRow(seeded, 229))));
        when(writeClient.updateRoom(any())).thenReturn(new UpdateRoomResult.Applied("ok"));

        RunReport report = run();

        assertThat(report.result()).isEqualTo(RunReport.Result.OK);
        ArgumentCaptor<UpdateRoomCommand> command = ArgumentCaptor.forClass(UpdateRoomCommand.class);
        verify(writeClient).updateRoom(command.capture());
        assertThat(command.getValue()).isEqualTo(new UpdateRoomCommand(YEAR, 1, 2008, seeded.subject(), 7, 1, 1, 800,
                90, seeded.buildingCode(), 229));
        SysacadSchedule after = schedule(seeded);
        assertThat(after.getKnownRoom()).isEqualTo(229);
        assertThat(after.getStatus()).isEqualTo(SysacadScheduleStatus.SYNCED);
        assertThat(logTypes(seeded)).containsExactly(ScheduleLogType.SEND_INTENT.name(),
                ScheduleLogType.SEND_CONFIRMED.name());
    }

    @Test
    @DisplayName("Ítem sin aceptar frena el envío de punta a punta: el horario queda en WAITING y no se llama a SysAcad")
    void unacceptedItemHoldsTheSend() {
        Seeded seeded = seed(true, true);
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.ONE_TIME_ROOM_CHANGE, seeded.academic().subjectId());
        RoomRequestItem item = testData.itemDePedido(request, seeded.academic().commissionId(), LocalDate.now().plusDays(10),
                RoomRequestStatus.IN_EVALUATION);
        roomRequestRepository.save(request);
        jdbc.update("INSERT INTO solicitud_item_asignacion (id_item, id_ocurrencia, id_aula, orden) "
                + "VALUES (?, ?, ?, 1)", item.getId(), seeded.occurrenceId(), seeded.target().getId());
        viewShows(seeded, 228);

        RunReport report = run();

        assertThat(report.result()).isEqualTo(RunReport.Result.OK);
        verify(writeClient, never()).updateRoom(any());
        verify(writeClient, never()).readSchedule(anyInt(), anyInt(), anyInt());
        SysacadSchedule after = schedule(seeded);
        assertThat(after.getStatus()).isEqualTo(SysacadScheduleStatus.WAITING);
        assertThat(after.getKnownRoom()).isEqualTo(228);
        assertThat(logTypes(seeded)).containsExactly(ScheduleLogType.WAITING.name());
    }

    @Test
    @DisplayName("SysacadScheduleStateWriter: markIntent persiste aunque quien llama falle después (REQUIRES_NEW)")
    void markIntentSurvivesCallerRollback() {
        Seeded seeded = seed(true, true);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            markIntent(seeded, 229);
            throw new IllegalStateException("falla del llamador");
        })).hasMessage("falla del llamador");

        assertThat(schedule(seeded).getStatus()).isEqualTo(SysacadScheduleStatus.SENDING);
        assertThat(logTypes(seeded)).containsExactly(ScheduleLogType.SEND_INTENT.name());
    }

    @Test
    @DisplayName("SysacadScheduleStateWriter: recordFailure y toReview persisten aunque quien llama falle después")
    void failureAndReviewSurviveCallerRollback() {
        Seeded seeded = seed(true, true);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            stateWriter.recordFailure(seeded.scheduleId(), "SERVER_ERROR", true, Instant.now().plusSeconds(60),
                    "HTTP 500", "cuerpo", "ERR-1");
            stateWriter.toReview(seeded.scheduleId(), SysacadReviewReason.HOUR_MISMATCH,
                    List.of(seeded.occurrenceId()));
            throw new IllegalStateException("falla del llamador");
        })).isInstanceOf(IllegalStateException.class);

        SysacadSchedule after = schedule(seeded);
        assertThat(after.getStatus()).isEqualTo(SysacadScheduleStatus.REVIEW);
        assertThat(after.getReviewReason()).isEqualTo(SysacadReviewReason.HOUR_MISMATCH);
        assertThat(after.getAttempts()).isEqualTo(1);
        assertThat(logTypes(seeded)).containsExactly(ScheduleLogType.SEND_FAILED.name(),
                ScheduleLogType.REVIEW_OPENED.name());
    }

    @Test
    @DisplayName("V57 aplica: sysacad_envio_estado tiene su fila única y el registro acepta jsonb")
    void migrationV57AppliesAndLogAcceptsJsonb() {
        Seeded seeded = seed(true, true);

        Integer rows = jdbc.queryForObject("SELECT count(*) FROM sysacad_envio_estado WHERE id = 1", Integer.class);
        Boolean applied = jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '57'", Boolean.class);
        stateWriter.toReview(seeded.scheduleId(), SysacadReviewReason.REQUEST_ON_FUTURE_CLASS, List.of(11L, 12L));

        assertThat(rows).isEqualTo(1);
        assertThat(applied).isTrue();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO sysacad_envio_estado (id) VALUES (2)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        Integer jsonLength = jdbc.queryForObject("SELECT jsonb_array_length(ocurrencias) FROM sysacad_horario_registro "
                + "WHERE id_horario = ? AND tipo = 'REVIEW_OPENED'", Integer.class, seeded.scheduleId());
        assertThat(jsonLength).isEqualTo(2);
        SysacadScheduleLogEntry entry = logRepository.findFirstByScheduleIdAndTypeOrderByIdDesc(
                seeded.scheduleId(), ScheduleLogType.REVIEW_OPENED).orElseThrow();
        assertThat(entry.getOccurrences()).contains("11").contains("12");
    }

    @Test
    @DisplayName("RecentSendLookup contra Postgres: un SEND_INTENT de hace 6 días 23 horas es reciente y uno de hace 7 días 1 hora no")
    void recentSendWindowAgainstDatabase() {
        Seeded seeded = seed(true, true);
        Instant now = Instant.now();
        insertIntent(seeded, 229, now.minus(Duration.ofDays(6)).minus(Duration.ofHours(23)));
        insertIntent(seeded, 230, now.minus(Duration.ofDays(7)).minus(Duration.ofHours(1)));
        SysacadSchedule schedule = schedule(seeded);

        assertThat(recentSendLookup.isRecentSend(schedule, seeded.buildingCode(), 229, now)).isTrue();
        assertThat(recentSendLookup.isRecentSend(schedule, seeded.buildingCode(), 230, now)).isFalse();
        assertThat(recentSendLookup.isRecentSend(schedule, seeded.buildingCode() + 1, 229, now)).isFalse();
    }

    @Test
    @DisplayName("RecentSendLookup contra Postgres: el envío en curso del horario cuenta aunque no haya filas en el registro")
    void sendingColumnsCountWithoutLogRows() {
        Seeded seeded = seed(true, true);
        jdbc.update("UPDATE sysacad_horario SET edificio_en_envio = ?, aula_en_envio = 229 WHERE id_horario = ?",
                seeded.buildingCode(), seeded.scheduleId());

        assertThat(recentSendLookup.isRecentSend(schedule(seeded), seeded.buildingCode(), 229, Instant.now()))
                .isTrue();
        assertThat(recentSendLookup.isRecentSend(schedule(seeded), seeded.buildingCode(), 230, Instant.now()))
                .isFalse();
    }

    @Test
    @DisplayName("El job de envío no publica un TaskScheduler ni un ScheduledExecutorService nuevo en el contexto")
    void jobPublishesNoSchedulerBeans() {
        Map<String, ScheduledExecutorService> executors = applicationContext
                .getBeansOfType(ScheduledExecutorService.class);
        Map<String, TaskScheduler> schedulers = applicationContext.getBeansOfType(TaskScheduler.class);

        assertThat(applicationContext.getBeansOfType(SysacadWriteJob.class)).hasSize(1);
        assertThat(executors).isEmpty();
        assertThat(schedulers.keySet()).noneMatch(name -> name.toLowerCase().contains("sysacad"));
    }

    private void insertIntent(Seeded seeded, int room, Instant at) {
        jdbc.update("INSERT INTO sysacad_horario_registro (id_horario, fecha, tipo, edificio_nuevo, aula_nuevo) "
                + "VALUES (?, ?, 'SEND_INTENT', ?, ?)", seeded.scheduleId(), Timestamp.from(at),
                seeded.buildingCode(), room);
    }
}
