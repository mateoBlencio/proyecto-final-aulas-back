package ar.edu.utn.frc.siga.allocation.events.validator;

import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
import ar.edu.utn.frc.siga.allocation.events.config.EventScheduleProperties;
import ar.edu.utn.frc.siga.allocation.events.exception.InvalidCommissionForSubjectException;
import ar.edu.utn.frc.siga.allocation.events.exception.InvalidEventScheduleException;
import ar.edu.utn.frc.siga.allocation.events.exception.MissingAcademicReferenceException;
import ar.edu.utn.frc.siga.allocation.events.exception.OccurrenceAlreadyPastException;
import ar.edu.utn.frc.siga.allocation.events.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.events.model.UniqueEventKind;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

/** Centraliza las reglas de negocio de horario y referencia académica de un evento. */
@Component
@RequiredArgsConstructor
public class EventScheduleValidator {

    private final EventScheduleProperties scheduleProperties;
    private final SubjectCommissionService subjectCommissionService;

    /**
     * Rechaza un horario de evento fuera de la ventana configurada ({@code siga.events.hours})
     * o cuya hora de fin no sea estrictamente posterior a la de inicio (CA3).
     */
    public void validateBusinessHours(LocalTime start, LocalTime end) {
        if (!end.isAfter(start)) {
            throw new InvalidEventScheduleException(
                    "La hora de fin (" + end + ") debe ser posterior a la hora de inicio (" + start + ").");
        }
        if (start.isBefore(scheduleProperties.getStart()) || end.isAfter(scheduleProperties.getEnd())) {
            throw new InvalidEventScheduleException("El horario " + start + "-" + end
                    + " está fuera de la ventana permitida (" + scheduleProperties.getStart()
                    + "-" + scheduleProperties.getEnd() + ").");
        }
    }

    /**
     * {@code subjectId} es obligatorio para Parcial/Trabajo Práctico/Examen final (tengan o no
     * comisión); para {@code OTRO} puede faltar. {@code commissionId} nunca es obligatorio por
     * sí solo, pero no puede existir sin {@code subjectId} (una comisión siempre pertenece a
     * una materia) — por eso un {@code OTRO} con comisión también exige materia.
     */
    public void validateAcademicReference(UniqueEventKind eventType, Long subjectId, Long commissionId) {
        boolean subjectRequired = eventType != UniqueEventKind.OTRO || commissionId != null;
        if (subjectRequired && subjectId == null) {
            throw new MissingAcademicReferenceException(
                    "subjectId es obligatorio para eventType=" + eventType
                            + (commissionId != null ? " cuando se indica commissionId" : ""));
        }
    }

    /**
     * Valida que {@code commissionId} sea realmente una comisión de {@code subjectId} (existe
     * un {@code SubjectCommission} que los vincula). Exclusivo de {@code UniqueEvent}: a
     * diferencia de {@code createRecurringEvent}, que solo valida que materia y comisión
     * existan cada una por separado (ver ADR-011), acá además se cruza que estén vinculadas —
     * es la validación completa que el ADR original prometía y que no llegó a implementarse en
     * el evento recurrente. No se llama con {@code subjectId}/{@code commissionId} nulos: en
     * ese punto ya pasó {@link #validateAcademicReference} y, si {@code commissionId} no es
     * null, {@code subjectId} tampoco lo es.
     */
    public void validateCommissionBelongsToSubject(Long subjectId, Long commissionId) {
        if (commissionId == null) {
            return;
        }
        try {
            subjectCommissionService.findBySubjectAndCommission(subjectId, commissionId);
        } catch (ResourceNotFoundException e) {
            throw new InvalidCommissionForSubjectException(
                    "La comisión " + commissionId + " no pertenece a la materia " + subjectId + ".");
        }
    }

    /** Ocurrencia ya ocurrida → no se puede modificar ni cancelar. */
    public void validateNotPast(Occurrence occurrence) {
        if (occurrence.isPast()) {
            throw new OccurrenceAlreadyPastException(
                    "La ocurrencia del " + occurrence.getDate() + " ya ocurrió.");
        }
    }
}
