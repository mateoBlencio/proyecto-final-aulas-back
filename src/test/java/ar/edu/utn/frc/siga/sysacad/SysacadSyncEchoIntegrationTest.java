package ar.edu.utn.frc.siga.sysacad;

import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadWriteMode;
import ar.edu.utn.frc.siga.settings.service.SettingsStore;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.common.security.SystemScope;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.sysacad.api.SysacadAllocationDto;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleStatus;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleRepository;
import ar.edu.utn.frc.siga.sysacad.internal.service.impl.SysacadCatalogSnapshotFactory;
import ar.edu.utn.frc.siga.sysacad.internal.sync.SysacadScheduleLoader;
import ar.edu.utn.frc.siga.sysacad.internal.sync.SysacadScheduleLoader.LoadResult;
import ar.edu.utn.frc.siga.sysacad.internal.sync.SysacadScheduleLoader.ResolvedScheduleRow;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Recorre con base real el tramo del sync que decide qué se escribe en SIGA: el loader de horarios (con su
 * reconciliador) y {@link AllocationService#syncFromSysacad}. No pasa por {@code AllocationSyncService}:
 * resolver comisión y evento por las fechas de cuatrimestre ata el escenario a la fecha de hoy y no
 * permite mezclar clases pasadas y futuras de forma estable.
 */
@Import(IntegrationTestData.class)
@DisplayName("Sync de SysAcad con escritura ON: eco de un envío (integración)")
@TestPropertySource(properties = {"siga.sysacad.enabled=true"})
class SysacadSyncEchoIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SettingsStore settingsStore;

    @BeforeEach
    void useWriteMode() {
        SysacadModeSupport.use(settingsStore, SysacadWriteMode.ON);
    }

    @AfterEach
    void resetWriteMode() {
        SysacadModeSupport.reset(settingsStore);
    }

    private static final int YEAR = 2096;
    private static final int SPECIALTY = 1;
    private static final int PLAN = 2008;
    private static final int SUBJECT = 115;
    private static final int COMMISSION = 7;
    private static final int BUILDING_CODE = 5;

    @MockitoBean
    private SysacadCatalogSnapshotFactory snapshotFactory;

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private AllocationService allocationService;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private SysacadScheduleLoader loader;
    @Autowired
    private SysacadScheduleRepository scheduleRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private Long eventId;
    private Classroom room228;
    private Classroom room229;
    private List<Long> pastOccurrenceIds;
    private List<Long> futureOccurrenceIds;
    private List<Long> allOccurrenceIds;

    @AfterEach
    void cleanScheduleRows() {
        jdbc.update("DELETE FROM sysacad_horario_registro WHERE id_horario IN "
                + "(SELECT id_horario FROM sysacad_horario WHERE anio = ?)", YEAR);
        jdbc.update("DELETE FROM sysacad_horario WHERE anio = ?", YEAR);
    }

    @BeforeEach
    void seedEventWithPastAndFutureClassesInRoom228() {
        cleanScheduleRows();
        IntegrationTestData.SubjectAndCommission sc = testData.materiaYComision();
        Building building = testData.edificio();
        room228 = testData.aula(building);
        room229 = testData.aula(building);

        LocalDate today = LocalDate.now();
        LocalDate start = today.with(TemporalAdjusters.previous(DayOfWeek.MONDAY)).minusWeeks(1);
        LocalDate end = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(1);
        eventId = testData.eventoRecurrente(sc.subjectId(), sc.commissionId(), DayOfWeek.MONDAY,
                LocalTime.of(18, 0), 90, start, end, 30);

        List<Occurrence> occurrences = occurrenceRepository.findByEvent_Id(eventId);
        pastOccurrenceIds = occurrences.stream().filter(o -> o.getDate().isBefore(today.minusDays(1)))
                .map(Occurrence::getId).toList();
        futureOccurrenceIds = occurrences.stream().filter(o -> o.getDate().isAfter(today.plusDays(1)))
                .map(Occurrence::getId).toList();
        allOccurrenceIds = occurrences.stream().map(Occurrence::getId).toList();
        assertThat(pastOccurrenceIds).isNotEmpty();
        assertThat(futureOccurrenceIds).isNotEmpty();

        syncAllocations(room228, Set.of(eventId));
        assertThat(snapshot().values()).allSatisfy(allocation -> {
            assertThat(allocation.classroomId()).isEqualTo(room228.getId());
            assertThat(allocation.source()).isEqualTo(AllocationSource.SYSACAD);
        });

        scheduleRepository.save(SysacadSchedule.builder()
                .year(YEAR).specialty(SPECIALTY).plan(PLAN).subject(SUBJECT).commission(COMMISSION)
                .dayOfWeek(DayOfWeek.MONDAY.getValue()).semester(1)
                .courseCode("1K1").startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(19, 30)).durationMinutes(90)
                .knownBuilding(BUILDING_CODE).knownRoom(228).baseBuilding(BUILDING_CODE).baseRoom(228)
                .eventId1(eventId)
                .status(SysacadScheduleStatus.PENDING)
                .sendingBuilding(BUILDING_CODE).sendingRoom(229)
                .attempts(2)
                .build());
    }

    private record AllocationState(Long classroomId, AllocationSource source) {
    }

    private Map<Long, AllocationState> snapshot() {
        Map<Long, AllocationState> state = new HashMap<>();
        for (Long occurrenceId : allOccurrenceIds) {
            Allocation allocation = allocationRepository.findByOccurrenceId(occurrenceId).orElseThrow();
            state.put(occurrenceId, new AllocationState(allocation.getClassroomId(), allocation.getSource()));
        }
        return state;
    }

    private int syncAllocations(Classroom classroom, Set<Long> eventIdsToUpdate) {
        return SystemScope.call(() -> allocationService.syncFromSysacad(
                List.of(new AllocationItem(new AllocationTarget.Event(eventId), classroom.getId())),
                eventIdsToUpdate));
    }

    private LoadResult loadRowShowing229() {
        SysacadAllocationDto row = new SysacadAllocationDto("1K1", SUBJECT, SPECIALTY, PLAN, DayOfWeek.MONDAY,
                LocalTime.of(18, 0), 90, LocalTime.of(19, 30), COMMISSION, 1, 229, BUILDING_CODE);
        return loader.load(List.of(new ResolvedScheduleRow(row, YEAR, Set.of(eventId), null)));
    }

    @Test
    @DisplayName("una fila que muestra 229 con envío pendiente de 229 confirma el envío y no cambia ninguna asignación")
    void echoOfPendingSendConfirmsSendAndKeepsAllocations() {
        Map<Long, AllocationState> before = snapshot();

        LoadResult result = loadRowShowing229();
        int affectedByBase = syncAllocations(room228, result.eventIdsToUpdate());
        int affectedByRow = syncAllocations(room229, result.eventIdsToUpdate());

        SysacadSchedule schedule = scheduleRepository.findAll().stream().filter(s -> s.getYear() == YEAR)
                .findFirst().orElseThrow();
        assertThat(schedule.getStatus()).isEqualTo(SysacadScheduleStatus.SYNCED);
        assertThat(schedule.getKnownRoom()).isEqualTo(229);
        assertThat(schedule.getBaseRoom()).isEqualTo(228);
        assertThat(schedule.getSendingRoom()).isNull();
        assertThat(schedule.getAttempts()).isZero();
        assertThat(schedule.getDifferentRoom()).isNull();
        assertThat(result.eventIdsToUpdate()).isEmpty();
        assertThat(result.directChangeScheduleIds()).isEmpty();
        assertThat(affectedByBase).isZero();
        assertThat(affectedByRow).isZero();
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("control: si el evento sí estuviera en el set, solo las clases futuras pasarían a 229 y las pasadas quedan en 228")
    void controlEventInSetUpdatesOnlyFutureClasses() {
        int affected = syncAllocations(room229, Set.of(eventId));

        Map<Long, AllocationState> after = snapshot();
        assertThat(affected).isGreaterThanOrEqualTo(futureOccurrenceIds.size());
        assertThat(futureOccurrenceIds.stream().map(after::get).map(AllocationState::classroomId)
                .collect(Collectors.toSet())).containsExactly(room229.getId());
        assertThat(pastOccurrenceIds.stream().map(after::get).map(AllocationState::classroomId)
                .collect(Collectors.toSet())).containsExactly(room228.getId());
    }
}
