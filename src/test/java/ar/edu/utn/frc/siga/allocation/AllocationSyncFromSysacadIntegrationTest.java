package ar.edu.utn.frc.siga.allocation;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Extremo a extremo de la política de precedencia del sync ASIGNACIONES de SysAcad
 * ({@link AllocationService#syncFromSysacad}, ver .claude/docs/plan-sync-eventos-sysacad.md §4): crea
 * asignaciones {@code SYSACAD} donde no había ninguna, actualiza las que ya eran {@code SYSACAD}, y no
 * toca las que tiene un humano (source {@code MANUAL}) — el propio caso de "convivencia" que motiva la
 * política.
 *
 * <p>Requiere Testcontainers (Docker) para levantar Postgres — ver {@link AbstractIntegrationTest}.
 */
@Import(IntegrationTestData.class)
@DisplayName("AllocationService.syncFromSysacad (integración)")
class AllocationSyncFromSysacadIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private AllocationService allocationService;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private AllocationRepository allocationRepository;

    @Test
    @DisplayName("crea SYSACAD donde no había asignación, actualiza la propia en el segundo sync, y nunca toca la que reasignó un humano")
    void syncFromSysacadCreatesUpdatesOwnAndSkipsForeign() {
        IntegrationTestData.SubjectAndCommission sc = testData.materiaYComision();
        Building building = testData.edificio();
        Classroom sysacadClassroom = testData.aula(building);
        Classroom manualClassroom = testData.aula(building);
        Classroom sysacadClassroomV2 = testData.aula(building);

        // Dos semanas -> dos ocurrencias (RecurringEvent.toOccurrences expande semana a semana).
        LocalDate monday = LocalDate.now().plusYears(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDate endDate = monday.plusWeeks(1);
        Long eventId = testData.eventoRecurrente(sc.subjectId(), sc.commissionId(), DayOfWeek.MONDAY,
                LocalTime.of(18, 0), 90, monday, endDate, 30);

        List<Occurrence> occurrences = occurrenceRepository.findByEvent_Id(eventId);
        assertThat(occurrences).hasSize(2);
        Long firstOccurrenceId = occurrences.get(0).getId();
        Long secondOccurrenceId = occurrences.get(1).getId();

        // 1) Primer sync: ninguna ocurrencia tenía asignación -> crea las dos, source=SYSACAD.
        int affectedFirstSync = allocationService.syncFromSysacad(
                List.of(new AllocationItem(new AllocationTarget.Event(eventId), sysacadClassroom.getId())));

        assertThat(affectedFirstSync).isEqualTo(2);
        assertThat(allocationByOccurrence(firstOccurrenceId)).satisfies(a -> {
            assertThat(a.getSource()).isEqualTo(AllocationSource.SYSACAD);
            assertThat(a.getClassroomId()).isEqualTo(sysacadClassroom.getId());
            assertThat(a.getObservation()).isEqualTo("Recuperado de SysAcad");
        });
        assertThat(allocationByOccurrence(secondOccurrenceId).getSource()).isEqualTo(AllocationSource.SYSACAD);

        // 2) Un humano reasigna a mano la primera ocurrencia: pasa a source=MANUAL.
        allocationService.reallocate(AllocationCommand.manual(
                List.of(new AllocationItem(new AllocationTarget.Occurrences(List.of(firstOccurrenceId)), manualClassroom.getId())),
                "Reasignado a mano"));
        assertThat(allocationByOccurrence(firstOccurrenceId).getSource()).isEqualTo(AllocationSource.MANUAL);

        // 3) Segundo sync de SysAcad, con un aula distinta para todo el evento: la ocurrencia MANUAL
        //    no se toca; la que seguía siendo SYSACAD se actualiza.
        int affectedSecondSync = allocationService.syncFromSysacad(
                List.of(new AllocationItem(new AllocationTarget.Event(eventId), sysacadClassroomV2.getId())));

        assertThat(affectedSecondSync).isEqualTo(1);

        Allocation manualAllocation = allocationByOccurrence(firstOccurrenceId);
        assertThat(manualAllocation.getSource()).isEqualTo(AllocationSource.MANUAL);
        assertThat(manualAllocation.getClassroomId()).isEqualTo(manualClassroom.getId());
        assertThat(manualAllocation.getObservation()).isEqualTo("Reasignado a mano");

        Allocation sysacadAllocation = allocationByOccurrence(secondOccurrenceId);
        assertThat(sysacadAllocation.getSource()).isEqualTo(AllocationSource.SYSACAD);
        assertThat(sysacadAllocation.getClassroomId()).isEqualTo(sysacadClassroomV2.getId());
        assertThat(sysacadAllocation.getObservation()).isEqualTo("Actualizado por sync de SysAcad");
    }

    private Allocation allocationByOccurrence(Long occurrenceId) {
        Optional<Allocation> allocation = allocationRepository.findByOccurrenceId(occurrenceId);
        assertThat(allocation).as("asignación para la ocurrencia " + occurrenceId).isPresent();
        return allocation.get();
    }
}
