package ar.edu.utn.frc.siga.allocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.exception.ReallocationConflictException;
import ar.edu.utn.frc.siga.allocation.exception.OverlapObservationRequiredException;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.model.ClassroomMovements;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.SysacadDirectChangeCommand;
import ar.edu.utn.frc.siga.allocation.service.command.SysacadDirectChangeResult;
import ar.edu.utn.frc.siga.common.security.SystemScope;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData.SubjectAndCommission;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@Import(IntegrationTestData.class)
@RecordApplicationEvents
@DisplayName("AllocationService.applySysacadDirectChange (integración)")
class AllocationSysacadDirectChangeIntegrationTest extends AbstractIntegrationTest {

    private static final String REASON = "Cambio directo en SysAcad";
    private static final String ACTOR = "SYSACAD";
    private static final LocalDate PAST_DATE = LocalDate.of(2020, 1, 6);

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private AllocationService allocationService;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ApplicationEvents events;

    private Building building;
    private Classroom oldRoom;
    private Classroom targetRoom;

    private Long newEvent(SubjectAndCommission academic, LocalDate from, int weeks) {
        return testData.eventoRecurrente(academic.subjectId(), academic.commissionId(), from.getDayOfWeek(),
                LocalTime.of(18, 0), 90, from, from.plusWeeks(weeks - 1), 30);
    }

    private LocalDate firstMonday() {
        return LocalDate.now().plusYears(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
                .plusWeeks(IntegrationTestData.nextSeq() % 400);
    }

    private void rooms() {
        building = testData.edificio();
        oldRoom = testData.aula(building);
        targetRoom = testData.aula(building);
    }

    private void allocate(Occurrence occurrence, Classroom classroom, AllocationSource source) {
        allocationRepository.save(Allocation.builder().occurrenceId(occurrence.getId())
                .classroomId(classroom.getId()).source(source).build());
    }

    private Allocation allocationOf(Occurrence occurrence) {
        return allocationRepository.findByOccurrenceId(occurrence.getId()).orElseThrow();
    }

    private SysacadDirectChangeResult directChange(Long... eventIds) {
        return directChange(targetRoom.getId(), false, ACTOR, eventIds);
    }

    private SysacadDirectChangeResult directChange(Long classroomId, boolean applyManualRules, String actor,
            Long... eventIds) {
        return SystemScope.call(() -> allocationService.applySysacadDirectChange(
                new SysacadDirectChangeCommand(List.of(eventIds), classroomId, REASON, actor, applyManualRules)));
    }

    private int lastRevision() {
        return jdbc.queryForObject("SELECT COALESCE(MAX(rev), 0) FROM revinfo", Integer.class);
    }

    private record Fixture(Long eventId, List<Occurrence> occurrences) {
    }

    private Fixture mixedFixture() {
        rooms();
        SubjectAndCommission academic = testData.materiaYComision();
        Long eventId = newEvent(academic, firstMonday(), 8);
        List<Occurrence> occurrences = occurrenceRepository.findByEvent_Id(eventId).stream()
                .sorted(java.util.Comparator.comparing(Occurrence::getDate)).toList();
        assertThat(occurrences).hasSize(8);
        allocate(occurrences.get(0), oldRoom, AllocationSource.MANUAL);
        allocate(occurrences.get(1), oldRoom, AllocationSource.AUTOMATIC);
        allocate(occurrences.get(2), oldRoom, AllocationSource.IMPORTED);
        allocate(occurrences.get(3), oldRoom, AllocationSource.SYSACAD);
        allocate(occurrences.get(5), oldRoom, AllocationSource.MANUAL);
        allocate(occurrences.get(6), oldRoom, AllocationSource.MANUAL);
        jdbc.update("UPDATE ocurrencia SET fecha = ? WHERE id_ocurrencia = ?", PAST_DATE, occurrences.get(5).getId());
        jdbc.update("UPDATE ocurrencia SET id_ocurrencia_principal = ?, orden_aula = 2 WHERE id_ocurrencia = ?",
                occurrences.get(0).getId(), occurrences.get(6).getId());
        jdbc.update("UPDATE ocurrencia SET estado = 'ROOM_RELEASED', motivo_liberacion = 'OTHER' "
                + "WHERE id_ocurrencia = ?", occurrences.get(7).getId());
        return new Fixture(eventId, occurrences);
    }

    @Test
    @DisplayName("pisa MANUAL, AUTOMATIC, IMPORTED y SYSACAD en clases futuras, deja source SYSACAD y crea la que no tenía asignación")
    void overwritesEverySourceOnFutureClasses() {
        Fixture fixture = mixedFixture();
        List<Occurrence> occ = fixture.occurrences();

        SysacadDirectChangeResult result = directChange(fixture.eventId());

        assertThat(result).isInstanceOf(SysacadDirectChangeResult.Applied.class);
        for (int i = 0; i <= 3; i++) {
            Allocation allocation = allocationOf(occ.get(i));
            assertThat(allocation.getClassroomId()).as("clase %d", i).isEqualTo(targetRoom.getId());
            assertThat(allocation.getSource()).as("clase %d", i).isEqualTo(AllocationSource.SYSACAD);
            assertThat(allocation.getObservation()).isEqualTo(REASON);
        }
        Allocation created = allocationOf(occ.get(4));
        assertThat(created.getClassroomId()).isEqualTo(targetRoom.getId());
        assertThat(created.getSource()).isEqualTo(AllocationSource.SYSACAD);
        List<ClassroomMovements.Change> changes = ((SysacadDirectChangeResult.Applied) result).changes();
        assertThat(changes).hasSize(5);
        assertThat(changes).filteredOn(c -> c.fromClassroomId() == null).hasSize(1);
        assertThat(changes).filteredOn(c -> oldRoom.getId().equals(c.fromClassroomId())).hasSize(4);
        assertThat(changes).allMatch(c -> targetRoom.getId().equals(c.toClassroomId()));
    }

    @Test
    @DisplayName("no toca las clases pasadas, los espejos ni las liberadas")
    void leavesPastMirrorAndReleasedClassesUntouched() {
        Fixture fixture = mixedFixture();
        List<Occurrence> occ = fixture.occurrences();

        directChange(fixture.eventId());

        for (int untouched : new int[] { 5, 6 }) {
            Allocation allocation = allocationOf(occ.get(untouched));
            assertThat(allocation.getClassroomId()).as("clase %d", untouched).isEqualTo(oldRoom.getId());
            assertThat(allocation.getSource()).isEqualTo(AllocationSource.MANUAL);
        }
        assertThat(allocationRepository.findByOccurrenceId(occ.get(7).getId())).isEmpty();
    }

    @Test
    @DisplayName("con el aula ocupada devuelve Occupied sin escribir nada: ni asignaciones, ni auditoría, ni eventos")
    void occupiedRoomWritesNothing() {
        rooms();
        LocalDate monday = firstMonday();
        Long eventId = newEvent(testData.materiaYComision(), monday, 1);
        Long occupantEventId = newEvent(testData.materiaYComision(), monday, 1);
        Occurrence occurrence = occurrenceRepository.findByEvent_Id(eventId).getFirst();
        Occurrence occupant = occurrenceRepository.findByEvent_Id(occupantEventId).getFirst();
        allocate(occurrence, oldRoom, AllocationSource.MANUAL);
        allocate(occupant, targetRoom, AllocationSource.MANUAL);
        int revisionBefore = lastRevision();

        SysacadDirectChangeResult result = directChange(eventId);

        assertThat(result).isInstanceOfSatisfying(SysacadDirectChangeResult.Occupied.class,
                occupied -> assertThat(occupied.occurrenceIds()).containsExactly(occurrence.getId()));
        Allocation unchanged = allocationOf(occurrence);
        assertThat(unchanged.getClassroomId()).isEqualTo(oldRoom.getId());
        assertThat(unchanged.getSource()).isEqualTo(AllocationSource.MANUAL);
        assertThat(allocationOf(occupant).getClassroomId()).isEqualTo(targetRoom.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asignacion_aula_aud a JOIN asignacion_aula x "
                + "ON x.id_asignacion = a.id_asignacion WHERE a.rev > ? AND x.id_ocurrencia = ?", Integer.class,
                revisionBefore, occurrence.getId())).isZero();
        assertThat(events.stream(ClassroomMovements.class).toList()).isEmpty();
    }

    @Test
    @DisplayName("deja una sola operación en auditoría para todas las clases cambiadas")
    void leavesSingleAuditOperation() {
        Fixture fixture = mixedFixture();
        int revisionBefore = lastRevision();

        directChange(fixture.eventId());

        List<Map<String, Object>> operations = jdbc.queryForList(
                "SELECT DISTINCT r.operacion_id, r.descripcion FROM revinfo r "
                        + "JOIN asignacion_aula_aud a ON a.rev = r.rev WHERE r.rev > ?", revisionBefore);
        assertThat(operations).hasSize(1);
        assertThat(operations.getFirst().get("operacion_id")).isNotNull();
        assertThat((String) operations.getFirst().get("descripcion")).startsWith(REASON);
        Integer auditRows = jdbc.queryForObject("SELECT count(*) FROM asignacion_aula_aud WHERE rev > ?",
                Integer.class, revisionBefore);
        assertThat(auditRows).isEqualTo(5);
    }

    @Test
    @DisplayName("publica un ClassroomMovements con actor y origen SYSACAD aun bajo SystemScope, con el operacion_id de la auditoría")
    void publishesMovementsWithSysacadActorAndSource() {
        Fixture fixture = mixedFixture();
        int revisionBefore = lastRevision();

        SysacadDirectChangeResult result = directChange(fixture.eventId());

        List<ClassroomMovements> published = events.stream(ClassroomMovements.class).toList();
        assertThat(published).hasSize(1);
        ClassroomMovements movements = published.getFirst();
        assertThat(movements.actor()).isEqualTo("SYSACAD");
        assertThat(movements.source()).isEqualTo(AllocationSource.SYSACAD);
        assertThat(movements.reason()).isEqualTo(REASON);
        assertThat(movements.changes()).isEqualTo(((SysacadDirectChangeResult.Applied) result).changes());
        String auditedOperation = jdbc.queryForObject("SELECT DISTINCT r.operacion_id FROM revinfo r "
                + "JOIN asignacion_aula_aud a ON a.rev = r.rev WHERE r.rev > ?", String.class, revisionBefore);
        assertThat(movements.operationId()).isEqualTo(auditedOperation);
        var lastRevision = allocationService.findLastRevision(fixture.occurrences().get(0).getId()).orElseThrow();
        assertThat(lastRevision.operationId()).isEqualTo(auditedOperation);
    }

    @Test
    @DisplayName("findLastRevision de una ocurrencia sin asignación no devuelve nada")
    void lastRevisionOfUnallocatedOccurrenceIsEmpty() {
        rooms();
        Long eventId = newEvent(testData.materiaYComision(), firstMonday(), 1);
        Occurrence occurrence = occurrenceRepository.findByEvent_Id(eventId).getFirst();

        assertThat(allocationService.findLastRevision(occurrence.getId())).isEmpty();
    }

    @Test
    @DisplayName("sin clases futuras que cambiar no publica movimientos ni escribe")
    void withoutFutureClassesPublishesNothing() {
        rooms();
        Long eventId = newEvent(testData.materiaYComision(), firstMonday(), 1);
        Occurrence occurrence = occurrenceRepository.findByEvent_Id(eventId).getFirst();
        jdbc.update("UPDATE ocurrencia SET fecha = ? WHERE id_ocurrencia = ?", PAST_DATE, occurrence.getId());

        SysacadDirectChangeResult result = directChange(eventId);

        assertThat(result).isEqualTo(new SysacadDirectChangeResult.Applied(List.of()));
        assertThat(events.stream(ClassroomMovements.class).toList()).isEmpty();
    }

    private Long eventAt(LocalDate monday, LocalTime start) {
        SubjectAndCommission academic = testData.materiaYComision();
        return testData.eventoRecurrente(academic.subjectId(), academic.commissionId(), monday.getDayOfWeek(), start,
                90, monday, monday, 30);
    }

    @Test
    @DisplayName("el actor del comando viaja en ClassroomMovements, aun si no es SYSACAD")
    void movementsCarryTheActorOfTheCommand() {
        Fixture fixture = mixedFixture();

        directChange(targetRoom.getId(), false, "subsecretaria@frc.utn.edu.ar", fixture.eventId());

        List<ClassroomMovements> published = events.stream(ClassroomMovements.class).toList();
        assertThat(published).hasSize(1);
        assertThat(published.getFirst().actor()).isEqualTo("subsecretaria@frc.utn.edu.ar");
        assertThat(published.getFirst().source()).isEqualTo(AllocationSource.SYSACAD);
    }

    @Test
    @DisplayName("classroomId nulo: borra las asignaciones futuras, deja las clases pendientes de aula y no las libera")
    void nullClassroomLeavesFutureClassesNeedingRoom() {
        Fixture fixture = mixedFixture();
        List<Occurrence> occ = fixture.occurrences();

        SysacadDirectChangeResult result = directChange(null, false, ACTOR, fixture.eventId());

        List<ClassroomMovements.Change> changes = ((SysacadDirectChangeResult.Applied) result).changes();
        assertThat(changes).hasSize(4);
        assertThat(changes).allMatch(c -> c.toClassroomId() == null && oldRoom.getId().equals(c.fromClassroomId()));
        for (int i = 0; i <= 3; i++) {
            assertThat(allocationRepository.findByOccurrenceId(occ.get(i).getId())).as("clase %d", i).isEmpty();
            assertThat(statusOf(occ.get(i))).as("clase %d", i).isEqualTo("NEEDS_ROOM");
        }
        assertThat(allocationRepository.findByOccurrenceId(occ.get(5).getId())).isPresent();
        assertThat(statusOf(occ.get(7))).isEqualTo("ROOM_RELEASED");
        List<ClassroomMovements> published = events.stream(ClassroomMovements.class).toList();
        assertThat(published).hasSize(1);
        assertThat(published.getFirst().actor()).isEqualTo(ACTOR);
    }

    private String statusOf(Occurrence occurrence) {
        return jdbc.queryForObject("SELECT estado FROM ocurrencia WHERE id_ocurrencia = ?", String.class,
                occurrence.getId());
    }

    @Test
    @DisplayName("applyManualRules con solapamiento tolerado y observación: aplica el cambio y conserva la observación")
    void manualRulesToleratedOverlapWithObservationApplies() {
        rooms();
        LocalDate monday = firstMonday();
        Long eventId = eventAt(monday, LocalTime.of(18, 0));
        Occurrence occurrence = occurrenceRepository.findByEvent_Id(eventId).getFirst();
        Occurrence occupant = occurrenceRepository.findByEvent_Id(eventAt(monday, LocalTime.of(19, 0))).getFirst();
        allocate(occurrence, oldRoom, AllocationSource.MANUAL);
        allocate(occupant, targetRoom, AllocationSource.MANUAL);

        SysacadDirectChangeResult result = directChange(targetRoom.getId(), true, ACTOR, eventId);

        assertThat(result).isInstanceOf(SysacadDirectChangeResult.Applied.class);
        Allocation changed = allocationOf(occurrence);
        assertThat(changed.getClassroomId()).isEqualTo(targetRoom.getId());
        assertThat(changed.getObservation()).isEqualTo(REASON);
        assertThat(allocationOf(occupant).getClassroomId()).isEqualTo(targetRoom.getId());
    }

    @Test
    @DisplayName("applyManualRules con solapamiento tolerado y observación vacía: falla y no escribe")
    void manualRulesToleratedOverlapWithoutObservationFails() {
        rooms();
        LocalDate monday = firstMonday();
        Long eventId = eventAt(monday, LocalTime.of(18, 0));
        Occurrence occurrence = occurrenceRepository.findByEvent_Id(eventId).getFirst();
        Occurrence occupant = occurrenceRepository.findByEvent_Id(eventAt(monday, LocalTime.of(19, 0))).getFirst();
        allocate(occurrence, oldRoom, AllocationSource.MANUAL);
        allocate(occupant, targetRoom, AllocationSource.MANUAL);

        assertThatThrownBy(() -> SystemScope.call(() -> allocationService.applySysacadDirectChange(
                new SysacadDirectChangeCommand(List.of(eventId), targetRoom.getId(), " ", ACTOR, true))))
                .isInstanceOf(OverlapObservationRequiredException.class);

        assertThat(allocationOf(occurrence).getClassroomId()).isEqualTo(oldRoom.getId());
        assertThat(events.stream(ClassroomMovements.class).toList()).isEmpty();
    }

    @Test
    @DisplayName("applyManualRules con solapamiento bloqueante: lanza el error de siempre y no escribe")
    void manualRulesBlockingOverlapThrowsAndWritesNothing() {
        rooms();
        LocalDate monday = firstMonday();
        Long eventId = eventAt(monday, LocalTime.of(18, 0));
        Occurrence occurrence = occurrenceRepository.findByEvent_Id(eventId).getFirst();
        Occurrence occupant = occurrenceRepository.findByEvent_Id(eventAt(monday, LocalTime.of(18, 0))).getFirst();
        allocate(occurrence, oldRoom, AllocationSource.MANUAL);
        allocate(occupant, targetRoom, AllocationSource.MANUAL);
        int revisionBefore = lastRevision();

        assertThatThrownBy(() -> directChange(targetRoom.getId(), true, ACTOR, eventId))
                .isInstanceOf(ReallocationConflictException.class);

        assertThat(allocationOf(occurrence).getClassroomId()).isEqualTo(oldRoom.getId());
        assertThat(allocationOf(occurrence).getSource()).isEqualTo(AllocationSource.MANUAL);
        assertThat(lastRevision()).isEqualTo(revisionBefore);
        assertThat(events.stream(ClassroomMovements.class).toList()).isEmpty();
    }
}
