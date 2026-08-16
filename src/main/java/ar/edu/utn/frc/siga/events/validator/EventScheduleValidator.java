package ar.edu.utn.frc.siga.events.validator;

import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
import ar.edu.utn.frc.siga.events.config.EventScheduleSettings;
import ar.edu.utn.frc.siga.events.exception.InvalidCommissionForSubjectException;
import ar.edu.utn.frc.siga.events.exception.InvalidEventScheduleException;
import ar.edu.utn.frc.siga.events.exception.MissingAcademicReferenceException;
import ar.edu.utn.frc.siga.events.exception.OccurrenceAlreadyPastException;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

@Component
@RequiredArgsConstructor
public class EventScheduleValidator {

    private final EventScheduleSettings scheduleSettings;
    private final SubjectCommissionService subjectCommissionService;

    public void validateBusinessHours(LocalTime start, LocalTime end) {
        if (!end.isAfter(start)) {
            throw new InvalidEventScheduleException(
                    "La hora de fin (" + end + ") debe ser posterior a la hora de inicio (" + start + ").");
        }
        if (start.isBefore(scheduleSettings.getStart()) || end.isAfter(scheduleSettings.getEnd())) {
            throw new InvalidEventScheduleException("El horario " + start + "-" + end
                    + " está fuera de la ventana permitida (" + scheduleSettings.getStart()
                    + "-" + scheduleSettings.getEnd() + ").");
        }
    }

    public void validateAcademicReference(UniqueEventKind eventType, Long subjectId, Long commissionId) {
        boolean subjectRequired = eventType != UniqueEventKind.OTRO || commissionId != null;
        if (subjectRequired && subjectId == null) {
            throw new MissingAcademicReferenceException(
                    "subjectId es obligatorio para eventType=" + eventType
                            + (commissionId != null ? " cuando se indica commissionId" : ""));
        }
    }

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

    public void validateNotPast(Occurrence occurrence) {
        if (occurrence.isPast()) {
            throw new OccurrenceAlreadyPastException(
                    "La ocurrencia del " + occurrence.getDate() + " ya ocurrió.");
        }
    }
}
