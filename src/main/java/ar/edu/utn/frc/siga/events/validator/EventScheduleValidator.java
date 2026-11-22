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
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Component
@NamedInterface("api")
@RequiredArgsConstructor
public class EventScheduleValidator {

    private final EventScheduleSettings scheduleSettings;
    private final SubjectCommissionService subjectCommissionService;

    public void validateBusinessHours(LocalTime start, LocalTime end) {
        if (!end.isAfter(start)) {
            throw new InvalidEventScheduleException(
                    "La hora de fin (" + end + ") debe ser posterior a la hora de inicio (" + start + ").");
        }
        LocalTime windowEnd = scheduleSettings.getEnd();
        boolean endOfDay = LocalTime.MIDNIGHT.equals(windowEnd);
        if (start.isBefore(scheduleSettings.getStart()) || (!endOfDay && end.isAfter(windowEnd))) {
            throw new InvalidEventScheduleException("El horario " + start + "-" + end
                    + " está fuera de la ventana permitida (" + scheduleSettings.getStart()
                    + "-" + scheduleSettings.getEnd() + ").");
        }
    }

    public void validateAcademicReference(UniqueEventKind eventType, Long subjectId, Long commissionId) {
        boolean subjectOptional = eventType == UniqueEventKind.OTRO || eventType == UniqueEventKind.CONFERENCIA;
        boolean subjectRequired = !subjectOptional || commissionId != null;
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
        validateNotPast(occurrence.getDate(), occurrence.startTime());
    }

    public void validateNotPast(LocalDate date, LocalTime start) {
        if (LocalDateTime.now().isAfter(date.atTime(start))) {
            throw new OccurrenceAlreadyPastException("La ocurrencia del " + date + " ya ocurrió.");
        }
    }
}
