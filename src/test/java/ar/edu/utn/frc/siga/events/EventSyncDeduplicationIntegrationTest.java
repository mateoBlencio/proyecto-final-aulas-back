package ar.edu.utn.frc.siga.events;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.command.SyncRecurringEventCommand;
import ar.edu.utn.frc.siga.events.service.command.UpsertRecurringEventResult;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Convivencia entre el ingest de Excel y el sync EVENTOS de SysAcad (ver
 * .claude/docs/plan-sync-eventos-sysacad.md §4, "Convivencia con ingest — riesgo de duplicados"): ambos
 * caminos crean {@code RecurringEvent} para la misma realidad, pero comparten la misma clave natural
 * (subjectId, commissionId, dayOfWeek, startTime, startDate, endDate). Si el sync usa el mismo
 * {@code TermType} que produjo la fila de Excel (misma columna Dictado -> mismas fechas de
 * {@code TermType.startDate/endDate}), el evento ya importado se reutiliza en vez de duplicarse.
 *
 * <p>Requiere Testcontainers (Docker) para levantar Postgres — ver {@link AbstractIntegrationTest}.
 */
@Import(IntegrationTestData.class)
@DisplayName("Convivencia ingest-Excel / sync EVENTOS (integración)")
class EventSyncDeduplicationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData integrationTestData;
    @Autowired
    private AcademicEventService academicEventService;
    @Autowired
    private AcademicEventRepository eventRepository;
    @Autowired
    private OccurrenceRepository occurrenceRepository;

    @Test
    @DisplayName("un evento ya creado por el ingest de Excel y luego sincronizado desde SysAcad para el mismo slot "
            + "(mismo TermType -> mismas fechas) no se duplica: reusa el RecurringEvent existente")
    void syncReusesEventAlreadyImportedFromExcel() {
        int year = 2100 + (int) (IntegrationTestData.nextSeq() % 500);
        int code = (int) IntegrationTestData.nextSeq();
        Specialty specialty = integrationTestData.especialidad(code);
        StudyPlan plan = integrationTestData.planDeEstudio(code, specialty);
        Subject subject = integrationTestData.materia(
                code, "Materia-IT-" + code, plan, TermType.PRIMER_CUATRIMESTRE.getLabel());
        // CommissionSyncService siempre crea la comisión bajo período ANUAL (plan §4); el cuatrimestre
        // sólo decide las fechas del evento, no el período de la comisión.
        AcademicPeriod period = integrationTestData.periodoAcademico(year, TermType.ANUAL);
        Commission commission = integrationTestData.comision("CUR-" + code, period);
        integrationTestData.materiaComision(subject, commission, 30);

        // Camino ingest-Excel: mismo mecanismo que IngestRowResolver.resolve (findOrCreateRecurringEvent
        // -> createRecurringEvent), con las fechas que produce TermType.PRIMER_CUATRIMESTRE (columna
        // Dictado de la planilla) para este año.
        Long importedEventId = integrationTestData.eventoRecurrente(subject.getId(), commission.getId(),
                DayOfWeek.MONDAY, LocalTime.of(18, 30), 90,
                TermType.PRIMER_CUATRIMESTRE.startDate(year), TermType.PRIMER_CUATRIMESTRE.endDate(year), 25);

        long eventsBefore = eventRepository.count();
        long occurrencesBefore = occurrenceRepository.count();

        // Mismo slot, ahora sincronizado desde SysAcad: mismo HorarioCuatrimestre=1 -> mismo TermType ->
        // mismas fechas, así que resuelve a la misma clave natural.
        SyncRecurringEventCommand cmd = new SyncRecurringEventCommand(
                subject.getId(), commission.getId(), DayOfWeek.MONDAY, LocalTime.of(18, 30), 90, 30,
                TermType.PRIMER_CUATRIMESTRE.startDate(year), TermType.PRIMER_CUATRIMESTRE.endDate(year));

        UpsertRecurringEventResult result = academicEventService.syncRecurringEvent(cmd);

        assertThat(result.eventId()).isEqualTo(importedEventId);
        assertThat(result.created()).isFalse();
        assertThat(result.updated()).isTrue();

        assertThat(eventRepository.count()).isEqualTo(eventsBefore);
        assertThat(occurrenceRepository.count()).isEqualTo(occurrencesBefore);

        AcademicEventResponseDto updated = academicEventService.findById(importedEventId);
        assertThat(updated.enrolled()).isEqualTo(30);
    }
}
