package ar.edu.utn.frc.siga.sysacad;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.common.security.SystemScope;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.settings.service.SettingsStore;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.sysacad.api.SysacadAllocationDto;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadWriteMode;
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
import java.util.List;
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
 * Recorre con base real el tramo del sync que decide si SIGA sigue a SysAcad: el loader de horarios y
 * {@link AllocationService#syncFromSysacad}, igual que {@code SysacadSyncEchoIntegrationTest}.
 */
@Import(IntegrationTestData.class)
@DisplayName("Sync de SysAcad: SANDBOX conserva el aula de SIGA y OFF la pisa (integración)")
@TestPropertySource(properties = {"siga.sysacad.enabled=true"})
class SysacadSandboxSyncIntegrationTest extends AbstractIntegrationTest {

    private static final int YEAR = 2097;
    private static final int SPECIALTY = 1;
    private static final int PLAN = 2008;
    private static final int SUBJECT = 115;
    private static final int COMMISSION = 7;
    private static final int BUILDING_CODE = 5;

    @MockitoBean
    private SysacadCatalogSnapshotFactory snapshotFactory;

    @Autowired
    private SettingsStore settingsStore;
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
    private List<Long> futureOccurrenceIds;

    @AfterEach
    void cleanUp() {
        SysacadModeSupport.reset(settingsStore);
        cleanScheduleRows();
    }

    private void cleanScheduleRows() {
        jdbc.update("DELETE FROM sysacad_horario_registro WHERE id_horario IN "
                + "(SELECT id_horario FROM sysacad_horario WHERE anio = ?)", YEAR);
        jdbc.update("DELETE FROM sysacad_horario WHERE anio = ?", YEAR);
    }

    @BeforeEach
    void seedEventAllocatedInSigaToRoom229WhileSysacadShows228() {
        cleanScheduleRows();
        IntegrationTestData.SubjectAndCommission sc = testData.materiaYComision();
        Building building = testData.edificio();
        room228 = testData.aula(building);
        room229 = testData.aula(building);

        LocalDate today = LocalDate.now();
        LocalDate start = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDate end = start.plusWeeks(3);
        eventId = testData.eventoRecurrente(sc.subjectId(), sc.commissionId(), DayOfWeek.MONDAY,
                LocalTime.of(18, 0), 90, start, end, 30);
        List<Occurrence> occurrences = occurrenceRepository.findByEvent_Id(eventId);
        futureOccurrenceIds = occurrences.stream().map(Occurrence::getId).toList();
        assertThat(futureOccurrenceIds).isNotEmpty();

        syncAllocations(room229);
        assertThat(allocatedRooms()).containsExactly(room229.getId());

        scheduleRepository.save(SysacadSchedule.builder()
                .year(YEAR).specialty(SPECIALTY).plan(PLAN).subject(SUBJECT).commission(COMMISSION)
                .dayOfWeek(DayOfWeek.MONDAY.getValue()).semester(1)
                .courseCode("1K1").startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(19, 30)).durationMinutes(90)
                .knownBuilding(BUILDING_CODE).knownRoom(228).baseBuilding(BUILDING_CODE).baseRoom(228)
                .eventId1(eventId)
                .status(SysacadScheduleStatus.SYNCED)
                .build());
    }

    private Set<Long> allocatedRooms() {
        return futureOccurrenceIds.stream()
                .map(id -> allocationRepository.findByOccurrenceId(id).orElseThrow().getClassroomId())
                .collect(Collectors.toSet());
    }

    private void syncAllocations(Classroom classroom) {
        syncAllocations(classroom, Set.of(eventId));
    }

    private void syncAllocations(Classroom classroom, Set<Long> eventIdsToUpdate) {
        SystemScope.call(() -> allocationService.syncFromSysacad(
                List.of(new AllocationItem(new AllocationTarget.Event(eventId), classroom.getId())),
                eventIdsToUpdate));
    }

    private LoadResult loadRowShowing228() {
        SysacadAllocationDto row = new SysacadAllocationDto("1K1", SUBJECT, SPECIALTY, PLAN, DayOfWeek.MONDAY,
                LocalTime.of(18, 0), 90, LocalTime.of(19, 30), COMMISSION, 1, 228, BUILDING_CODE);
        return loader.load(List.of(new ResolvedScheduleRow(row, YEAR, Set.of(eventId), null)));
    }

    @Test
    @DisplayName("SANDBOX: la vista muestra el aula vieja 228 y el aula 229 de SIGA no se pisa")
    void sandboxKeepsSigaRoom() {
        SysacadModeSupport.use(settingsStore, SysacadWriteMode.SANDBOX);

        LoadResult result = loadRowShowing228();
        syncAllocations(room228, result.eventIdsToUpdate());

        assertThat(result.eventIdsToUpdate()).isEmpty();
        assertThat(allocatedRooms()).containsExactly(room229.getId());
    }

    @Test
    @DisplayName("OFF: con la misma vista, SIGA sigue a SysAcad y pisa el aula 229 con la 228")
    void offOverwritesSigaRoom() {
        SysacadModeSupport.use(settingsStore, SysacadWriteMode.OFF);

        LoadResult result = loadRowShowing228();
        syncAllocations(room228, result.eventIdsToUpdate());

        assertThat(result.eventIdsToUpdate()).containsExactly(eventId);
        assertThat(allocatedRooms()).containsExactly(room228.getId());
    }
}
