package ar.edu.utn.frc.siga.roomrequest.validator;

import ar.edu.utn.frc.siga.events.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ClassScheduleService {

    private final AcademicEventService academicEventService;

    private List<ClassSlot> slots(Long subjectId, Long commissionId) {
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

    public List<ClassSlot> distinctSlots(Long subjectId, Long commissionId) {
        return collapseByDayAndHours(slots(subjectId, commissionId));
    }

    private static List<ClassSlot> collapseByDayAndHours(List<ClassSlot> slots) {
        Map<List<Object>, ClassSlot> byDayAndHours = new LinkedHashMap<>();
        for (ClassSlot slot : slots) {
            byDayAndHours.putIfAbsent(List.of(slot.dayOfWeek(), slot.startTime(), slot.endTime()), slot);
        }
        return List.copyOf(byDayAndHours.values());
    }

    public ClassSlot requireClassDay(Long subjectId, Long commissionId, DayOfWeek dayOfWeek) {
        List<ClassSlot> matching = distinctSlots(subjectId, commissionId).stream()
                .filter(slot -> slot.dayOfWeek() == dayOfWeek)
                .toList();
        if (matching.isEmpty()) {
            throw new InvalidRoomRequestException(
                    "La comisión " + commissionId + " no dicta clase los " + dayOfWeek + ".");
        }
        return requireSingleClass(matching, "los " + dayOfWeek, commissionId);
    }

    public ClassSlot requireClassDate(Long subjectId, Long commissionId, LocalDate date) {
        List<Long> eventIds = academicEventService.findClassOccurrences(subjectId, commissionId, date).stream()
                .filter(occurrence -> occurrence.date().equals(date))
                .map(OccurrenceResponseDto::eventId)
                .distinct()
                .toList();
        if (eventIds.isEmpty()) {
            throw new InvalidRoomRequestException(
                    "La comisión " + commissionId + " no tiene clase el " + date + ".");
        }
        List<ClassSlot> matching = collapseByDayAndHours(slots(subjectId, commissionId).stream()
                .filter(slot -> eventIds.contains(slot.recurringEventId()))
                .toList());
        if (matching.isEmpty()) {
            throw new InvalidRoomRequestException(
                    "No se pudo resolver el horario de cursado de la comisión " + commissionId
                            + " para el " + date + ".");
        }
        return requireSingleClass(matching, "el " + date, commissionId);
    }

    private static ClassSlot requireSingleClass(List<ClassSlot> classes, String when, Long commissionId) {
        if (classes.size() == 1) {
            return classes.getFirst();
        }
        String hours = classes.stream()
                .map(slot -> slot.startTime() + " a " + slot.endTime())
                .collect(Collectors.joining(", "));
        throw new InvalidRoomRequestException(
                "La comisión " + commissionId + " tiene más de una clase " + when + " (" + hours + "). "
                        + "Este trámite todavía no permite elegir cuál: comunicate con subsecretaría.");
    }
}
