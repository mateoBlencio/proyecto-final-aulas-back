package ar.edu.utn.frc.siga.roomrequest.validator;

import ar.edu.utn.frc.siga.events.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * Fachada de {@code roomrequest} sobre el cursado que vive en {@code events}: traduce los eventos
 * recurrentes a {@link CursadoSlot} y aplica el bloqueo de calendario del lado del backend (no se
 * confía en lo que deshabilitó el front).
 */
@Component
@RequiredArgsConstructor
public class CursadoScheduleService {

    private final AcademicEventService academicEventService;

    public List<CursadoSlot> slots(Long subjectId, Long commissionId) {
        return academicEventService.findRecurringEventsBySubjectAndCommission(subjectId, commissionId).stream()
                .map(r -> new CursadoSlot(r.id(), r.dayOfWeek(), r.startTime(), r.endTime()))
                .toList();
    }

    /** Fechas de cursado de la comisión desde {@code from} en adelante, sin repetir, ordenadas. */
    public List<LocalDate> cursadoDates(Long subjectId, Long commissionId, LocalDate from) {
        return academicEventService.findCursadoOccurrences(subjectId, commissionId, from).stream()
                .map(occurrence -> occurrence.date())
                .distinct()
                .toList();
    }

    /**
     * El slot del día pedido. Error si la comisión no dicta ese día, o si dicta más de un bloque ese
     * día (el pedido sólo indica el día: no hay forma de saber a qué bloque se refiere, así que se
     * rechaza en vez de derivar un horario al azar).
     */
    public CursadoSlot requireCursadoDay(Long subjectId, Long commissionId, DayOfWeek dayOfWeek) {
        List<CursadoSlot> matching = slots(subjectId, commissionId).stream()
                .filter(slot -> slot.dayOfWeek() == dayOfWeek)
                .toList();
        if (matching.isEmpty()) {
            throw new InvalidRoomRequestException(
                    "La comisión " + commissionId + " no dicta clase los " + dayOfWeek + ".");
        }
        if (matching.size() > 1) {
            throw new InvalidRoomRequestException(
                    "La comisión " + commissionId + " dicta más de un bloque los " + dayOfWeek
                            + "; indicá una fecha puntual en vez de un día de dictado.");
        }
        return matching.getFirst();
    }

    /**
     * El slot correspondiente a una fecha de cursado real (ocurrencia); error si esa fecha no tiene
     * clase. Resuelve el slot por el evento de la ocurrencia, no por el día de la semana, así una
     * comisión con más de un bloque el mismo día deriva el horario correcto.
     */
    public CursadoSlot requireCursadoDate(Long subjectId, Long commissionId, LocalDate date) {
        Long eventId = academicEventService.findCursadoOccurrences(subjectId, commissionId, date).stream()
                .filter(occurrence -> occurrence.date().equals(date))
                .map(OccurrenceResponseDto::eventId)
                .findFirst()
                .orElseThrow(() -> new InvalidRoomRequestException(
                        "La comisión " + commissionId + " no tiene clase el " + date + "."));
        return slots(subjectId, commissionId).stream()
                .filter(slot -> slot.recurringEventId().equals(eventId))
                .findFirst()
                .orElseThrow(() -> new InvalidRoomRequestException(
                        "No se pudo resolver el horario de cursado de la comisión " + commissionId
                                + " para el " + date + "."));
    }
}
