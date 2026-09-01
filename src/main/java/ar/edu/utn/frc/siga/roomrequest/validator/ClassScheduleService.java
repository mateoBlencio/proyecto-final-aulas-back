package ar.edu.utn.frc.siga.roomrequest.validator;

import ar.edu.utn.frc.siga.events.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ClassScheduleService {

    private final AcademicEventService academicEventService;

    public List<ClassSlot> slots(Long subjectId, Long commissionId) {
        return academicEventService.findRecurringEventsBySubjectAndCommission(subjectId, commissionId).stream()
                .map(r -> new ClassSlot(r.id(), r.dayOfWeek(), r.startTime(), r.endTime()))
                .toList();
    }

    public List<LocalDate> classDates(Long subjectId, Long commissionId, LocalDate from) {
        return academicEventService.findClassOccurrences(subjectId, commissionId, from).stream()
                .map(occurrence -> occurrence.date())
                .distinct()
                .toList();
    }

    public ClassSlot requireClassDay(Long subjectId, Long commissionId, DayOfWeek dayOfWeek) {
        List<ClassSlot> matching = distinctByHours(slots(subjectId, commissionId).stream()
                .filter(slot -> slot.dayOfWeek() == dayOfWeek)
                .toList());
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

    private static List<ClassSlot> distinctByHours(List<ClassSlot> slots) {
        Map<List<LocalTime>, ClassSlot> byHours = new LinkedHashMap<>();
        for (ClassSlot slot : slots) {
            byHours.putIfAbsent(List.of(slot.startTime(), slot.endTime()), slot);
        }
        return List.copyOf(byHours.values());
    }

    public ClassSlot requireClassDate(Long subjectId, Long commissionId, LocalDate date) {
        Long eventId = academicEventService.findClassOccurrences(subjectId, commissionId, date).stream()
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
