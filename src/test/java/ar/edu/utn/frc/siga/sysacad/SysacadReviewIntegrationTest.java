package ar.edu.utn.frc.siga.sysacad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.exception.OverlapObservationRequiredException;
import ar.edu.utn.frc.siga.allocation.exception.ReallocationConflictException;
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
import ar.edu.utn.frc.siga.sysacad.internal.client.view.ScheduleViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.exception.SysacadConflictException;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleStatus;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleRepository;
import ar.edu.utn.frc.siga.sysacad.internal.service.impl.SysacadCatalogSnapshotFactory;
import ar.edu.utn.frc.siga.sysacad.internal.write.RunReport;
import ar.edu.utn.frc.siga.sysacad.internal.write.review.SysacadReviewService;
import ar.edu.utn.frc.siga.sysacad.internal.write.SysacadWriteRun;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData.SubjectAndCommission;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(IntegrationTestData.class)
@DisplayName("Quedarse con lo de SysAcad (integración, decisión 4)")
@TestPropertySource(properties = {
        "siga.sysacad.enabled=true",
        "siga.sysacad.write.mode=ON",
        "siga.sysacad.write.job-delay=PT24H"
})
class SysacadReviewIntegrationTest extends AbstractIntegrationTest {

    private static final int YEAR = 2094;
    private static final String USER = "sub@frc.utn.edu.ar";
    private static final String OBSERVATION = "Lo pidió Subsecretaría";

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
    private SysacadReviewService reviewService;
    @Autowired
    private SysacadWriteRun writeRun;
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

    private record Seeded(long scheduleId, int buildingCode, Classroom base, Classroom target, Long eventId,
            List<Occurrence> occurrences, SubjectAndCommission academic) {
    }

    private Seeded seed(int readRoom, int classes) {
        int buildingCode = 20_000 + (int) (IntegrationTestData.nextSeq() % 1_000_000);
        Building building = testData.edificio();
        building.setBuildingCode(buildingCode);
        building = buildingRepository.save(building);
        Classroom base = testData.aulaConNumero(228, building, testData.tipoAulaPorDefecto(), 40);
        Classroom target = testData.aulaConNumero(229, building, testData.tipoAulaPorDefecto(), 40);

        SubjectAndCommission academic = testData.materiaYComision();
        LocalDate first = LocalDate.now().plusYears(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
                .plusWeeks(IntegrationTestData.nextSeq() % 500);
        Long eventId = testData.eventoRecurrente(academic.subjectId(), academic.commissionId(), first.getDayOfWeek(),
                LocalTime.of(8, 0), 90, first, first.plusWeeks(classes - 1), 30);
        List<Occurrence> occurrences = occurrenceRepository.findByEvent_Id(eventId).stream()
                .sorted(Comparator.comparing(Occurrence::getDate)).toList();
        for (Occurrence occurrence : occurrences) {
            allocationRepository.save(Allocation.builder().occurrenceId(occurrence.getId())
                    .classroomId(base.getId()).source(AllocationSource.MANUAL).build());
        }
        int subject = (int) IntegrationTestData.nextSeq();
        Long scheduleId = jdbc.queryForObject("INSERT INTO sysacad_horario (anio, especialidad, plan, materia, "
                + "comision, dia, cuatrimestre, curso, hora_comienzo, hora_fin, duracion, edificio_conocido, "
                + "aula_conocida, edificio_base, aula_base, id_evento_1, estado, motivo_revision, revision_desde, "
                + "edificio_diferente, aula_diferente, diferencia_detectada_en) "
                + "VALUES (?, 1, 2008, ?, 7, 1, 1, '1R1', '08:00', '09:30', 90, ?, 228, ?, 228, ?, 'REVIEW', "
                + "'ROOM_OCCUPIED', now(), ?, ?, now()) RETURNING id_horario", Long.class, YEAR, subject,
                buildingCode, buildingCode, eventId, buildingCode, readRoom);
        return new Seeded(scheduleId, buildingCode, base, target, eventId, occurrences, academic);
    }

    private Allocation allocationOf(Occurrence occurrence) {
        return allocationRepository.findByOccurrenceId(occurrence.getId()).orElseThrow();
    }

    private String statusOf(Occurrence occurrence) {
        return jdbc.queryForObject("SELECT estado FROM ocurrencia WHERE id_ocurrencia = ?", String.class,
                occurrence.getId());
    }

    private SysacadSchedule schedule(Seeded seeded) {
        return scheduleRepository.findById(seeded.scheduleId()).orElseThrow();
    }

    private int logRows(Seeded seeded, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM sysacad_horario_registro WHERE id_horario = ? AND tipo = ?",
                Integer.class, seeded.scheduleId(), type);
    }

    private Occurrence occupantOverlapping(Seeded seeded, Occurrence occurrence, LocalTime start) {
        Long eventId = testData.eventoRecurrente(seeded.academic().subjectId(), seeded.academic().commissionId(),
                occurrence.getDate().getDayOfWeek(), start, 90, occurrence.getDate(), occurrence.getDate(), 30);
        Occurrence occupant = occurrenceRepository.findByEvent_Id(eventId).getFirst();
        allocationRepository.save(Allocation.builder().occurrenceId(occupant.getId())
                .classroomId(seeded.target().getId()).source(AllocationSource.MANUAL).build());
        return occupant;
    }

    @Test
    @DisplayName("Aula ocupada con solapamiento tolerado: aplica con la observación, conserva el solapamiento y cierra la revisión")
    void occupiedRoomWithToleratedOverlapAppliesWithObservation() {
        Seeded seeded = seed(229, 1);
        Occurrence occurrence = seeded.occurrences().getFirst();
        Occurrence occupant = occupantOverlapping(seeded, occurrence, LocalTime.of(9, 0));

        reviewService.keepSysacad(seeded.scheduleId(), USER, OBSERVATION);

        Allocation changed = allocationOf(occurrence);
        assertThat(changed.getClassroomId()).isEqualTo(seeded.target().getId());
        assertThat(changed.getSource()).isEqualTo(AllocationSource.SYSACAD);
        assertThat(changed.getObservation()).isEqualTo(OBSERVATION);
        assertThat(allocationOf(occupant).getClassroomId()).isEqualTo(seeded.target().getId());
        SysacadSchedule after = schedule(seeded);
        assertThat(after.getStatus()).isEqualTo(SysacadScheduleStatus.SYNCED);
        assertThat(after.getKnownRoom()).isEqualTo(229);
        assertThat(after.getBaseRoom()).isEqualTo(229);
        assertThat(logRows(seeded, "REVIEW_RESOLVED")).isEqualTo(1);
    }

    @Test
    @DisplayName("Solapamiento tolerado con observación vacía: falla, no escribe y deja el horario en REVIEW")
    void toleratedOverlapWithBlankObservationFailsWithoutWriting() {
        Seeded seeded = seed(229, 1);
        Occurrence occurrence = seeded.occurrences().getFirst();
        occupantOverlapping(seeded, occurrence, LocalTime.of(9, 0));

        assertThatThrownBy(() -> reviewService.keepSysacad(seeded.scheduleId(), USER, " "))
                .isInstanceOf(OverlapObservationRequiredException.class);

        assertThat(allocationOf(occurrence).getClassroomId()).isEqualTo(seeded.base().getId());
        assertThat(schedule(seeded).getStatus()).isEqualTo(SysacadScheduleStatus.REVIEW);
        assertThat(logRows(seeded, "REVIEW_RESOLVED")).isZero();
    }

    @Test
    @DisplayName("Solapamiento bloqueante: responde el error de siempre y no escribe nada")
    void blockingOverlapFailsWithoutWriting() {
        Seeded seeded = seed(229, 1);
        Occurrence occurrence = seeded.occurrences().getFirst();
        occupantOverlapping(seeded, occurrence, LocalTime.of(8, 0));

        assertThatThrownBy(() -> reviewService.keepSysacad(seeded.scheduleId(), USER, OBSERVATION))
                .isInstanceOf(ReallocationConflictException.class);

        Allocation unchanged = allocationOf(occurrence);
        assertThat(unchanged.getClassroomId()).isEqualTo(seeded.base().getId());
        assertThat(unchanged.getSource()).isEqualTo(AllocationSource.MANUAL);
        SysacadSchedule after = schedule(seeded);
        assertThat(after.getStatus()).isEqualTo(SysacadScheduleStatus.REVIEW);
        assertThat(after.getKnownRoom()).isEqualTo(228);
        assertThat(logRows(seeded, "REVIEW_RESOLVED")).isZero();
    }

    @Test
    @DisplayName("Pedido en clases futuras: las pisa con el aula de SysAcad y el motivo queda en la operación")
    void requestOnFutureClassesIsOverwrittenWithReason() {
        Seeded seeded = seed(229, 2);
        Occurrence withRequest = seeded.occurrences().getFirst();
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.ONE_TIME_ROOM_CHANGE,
                seeded.academic().subjectId());
        RoomRequestItem item = testData.itemDePedido(request, seeded.academic().commissionId(),
                withRequest.getDate(), RoomRequestStatus.IN_EVALUATION);
        roomRequestRepository.save(request);
        jdbc.update("INSERT INTO solicitud_item_asignacion (id_item, id_ocurrencia, id_aula, orden) "
                + "VALUES (?, ?, ?, 1)", item.getId(), withRequest.getId(), seeded.base().getId());

        reviewService.keepSysacad(seeded.scheduleId(), USER, OBSERVATION);

        for (Occurrence occurrence : seeded.occurrences()) {
            Allocation allocation = allocationOf(occurrence);
            assertThat(allocation.getClassroomId()).isEqualTo(seeded.target().getId());
            assertThat(allocation.getObservation()).isEqualTo(OBSERVATION);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM revinfo WHERE descripcion LIKE 'Cambio directo en SysAcad%'"
                + " AND descripcion LIKE ? AND operacion_id IS NOT NULL", Integer.class, "%" + OBSERVATION + "%"))
                .isPositive();
    }

    @Test
    @DisplayName("Aula 999: las clases futuras quedan NEEDS_ROOM sin ROOM_RELEASED y la clase pasada no se toca")
    void sentinelRoomLeavesFutureClassesNeedingRoom() {
        Seeded seeded = seed(999, 3);
        Occurrence past = seeded.occurrences().get(0);
        jdbc.update("UPDATE ocurrencia SET fecha = ? WHERE id_ocurrencia = ?", LocalDate.of(2020, 1, 6), past.getId());

        reviewService.keepSysacad(seeded.scheduleId(), USER, OBSERVATION);

        assertThat(allocationOf(past).getClassroomId()).isEqualTo(seeded.base().getId());
        for (Occurrence future : seeded.occurrences().subList(1, 3)) {
            assertThat(allocationRepository.findByOccurrenceId(future.getId())).isEmpty();
            assertThat(statusOf(future)).isEqualTo("NEEDS_ROOM");
        }
        SysacadSchedule after = schedule(seeded);
        assertThat(after.getKnownRoom()).isEqualTo(999);
        assertThat(after.getBaseRoom()).isEqualTo(999);
        assertThat(after.getStatus()).isEqualTo(SysacadScheduleStatus.SYNCED);
    }

    @Test
    @DisplayName("Una clase ROOM_RELEASED no se toca, con aula real ni con 999")
    void releasedClassIsNeverTouched() {
        Seeded seeded = seed(229, 2);
        Occurrence released = seeded.occurrences().get(1);
        allocationRepository.delete(allocationOf(released));
        jdbc.update("UPDATE ocurrencia SET estado = 'ROOM_RELEASED', motivo_liberacion = 'OTHER' "
                + "WHERE id_ocurrencia = ?", released.getId());

        reviewService.keepSysacad(seeded.scheduleId(), USER, OBSERVATION);

        assertThat(statusOf(released)).isEqualTo("ROOM_RELEASED");
        assertThat(allocationRepository.findByOccurrenceId(released.getId())).isEmpty();
        assertThat(allocationOf(seeded.occurrences().get(0)).getClassroomId()).isEqualTo(seeded.target().getId());
    }

    @Test
    @DisplayName("Aula desconocida en SIGA: 409 y nada cambia")
    void unknownRoomIsConflict() {
        Seeded seeded = seed(777, 1);

        assertThatThrownBy(() -> reviewService.keepSysacad(seeded.scheduleId(), USER, OBSERVATION))
                .isInstanceOf(SysacadConflictException.class);

        assertThat(allocationOf(seeded.occurrences().getFirst()).getClassroomId()).isEqualTo(seeded.base().getId());
        assertThat(schedule(seeded).getStatus()).isEqualTo(SysacadScheduleStatus.REVIEW);
    }

    @Test
    @DisplayName("Un horario en REVIEW por horas cambiadas frena el job; al resolverlo el job vuelve a correr")
    void jobResumesAfterHoursChangedReviewIsResolved() {
        Seeded seeded = seed(228, 1);
        jdbc.update("UPDATE sysacad_horario SET motivo_revision = 'HOURS_CHANGED_AFTER_SEND' WHERE id_horario = ?",
                seeded.scheduleId());
        when(viewFetcher.fetch()).thenReturn(List.of());

        RunReport blocked = writeRun.run(Instant.now());
        reviewService.resendSiga(seeded.scheduleId(), USER);
        RunReport resumed = writeRun.run(Instant.now());

        assertThat(blocked.result()).isEqualTo(RunReport.Result.SKIPPED_HOURS_CHANGED);
        assertThat(resumed.result()).isNotEqualTo(RunReport.Result.SKIPPED_HOURS_CHANGED);
        assertThat(schedule(seeded).getStatus()).isEqualTo(SysacadScheduleStatus.SYNCED);
    }
}
