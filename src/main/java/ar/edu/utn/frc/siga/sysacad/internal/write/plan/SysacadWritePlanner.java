package ar.edu.utn.frc.siga.sysacad.internal.write.plan;

import ar.edu.utn.frc.siga.academic.dto.response.HolidayResponseDto;
import ar.edu.utn.frc.siga.academic.service.HolidayService;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.dto.response.SysacadRecurringEventRefDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.roomrequest.dto.response.OccurrenceRequestStateDto;
import ar.edu.utn.frc.siga.roomrequest.service.OccurrenceRequestQuery;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomSysacadRefDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleStatus;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadWritePlanner {

    private final SysacadScheduleRepository scheduleRepository;
    private final AcademicEventService academicEventService;
    private final OccurrenceService occurrenceService;
    private final AllocationService allocationService;
    private final ClassroomService classroomService;
    private final OccurrenceRequestQuery occurrenceRequestQuery;
    private final HolidayService holidayService;

    private record Candidate(SysacadSchedule schedule, List<OccurrenceSlotDto> slots, OccurrenceSlotDto next) {
    }

    @Transactional(readOnly = true)
    public List<PlannedSchedule> plan(LocalDateTime now) {
        Set<Long> currentEventIds = academicEventService.findSysacadRecurringEvents().stream()
                .filter(SysacadRecurringEventRefDto::sysacadEnabled)
                .map(SysacadRecurringEventRefDto::eventId)
                .collect(Collectors.toSet());
        List<SysacadSchedule> schedules = scheduleRepository.findPlannable(SysacadScheduleStatus.REVIEW).stream()
                .filter(s -> !eventIdsOf(s, currentEventIds).isEmpty())
                .toList();
        if (schedules.isEmpty()) {
            return List.of();
        }
        Set<Long> eventIds = schedules.stream()
                .flatMap(s -> eventIdsOf(s, currentEventIds).stream())
                .collect(Collectors.toSet());
        LocalDate today = now.toLocalDate();
        Set<LocalDate> holidays = holidayService.findBetween(today, today.plusYears(1)).stream()
                .map(HolidayResponseDto::date)
                .collect(Collectors.toSet());
        // Se piden 2 porque la clase de hoy puede haber terminado; hay a lo sumo una principal por evento y día.
        Map<Long, List<OccurrenceSlotDto>> slotsByEvent = occurrenceService
                .findNextSlotsByEvents(eventIds, today, holidays, 2).stream()
                .collect(Collectors.groupingBy(OccurrenceSlotDto::eventId));

        List<Candidate> candidates = new ArrayList<>();
        for (SysacadSchedule schedule : schedules) {
            List<OccurrenceSlotDto> slots = eventIdsOf(schedule, currentEventIds).stream()
                    .flatMap(id -> slotsByEvent.getOrDefault(id, List.of()).stream())
                    .toList();
            NextClassCalculator.findNext(schedule, slots, holidays, now)
                    .ifPresent(next -> candidates.add(new Candidate(schedule, slots, next)));
        }
        Set<Long> nextIds = candidates.stream().map(c -> c.next().occurrenceId()).collect(Collectors.toSet());
        Map<Long, Long> classroomByOccurrence = allocationService.findClassroomIdsByOccurrences(nextIds);
        Map<Long, ClassroomSysacadRefDto> refs = Maps.byId(
                classroomService.findSysacadRefs(new HashSet<>(classroomByOccurrence.values())),
                ClassroomSysacadRefDto::classroomId);
        Map<Long, OccurrenceRequestStateDto> states = occurrenceRequestQuery.findStates(nextIds);

        return candidates.stream()
                .sorted(Comparator.comparing((Candidate c) -> c.next().date()).thenComparing(c -> c.next().startTime()))
                .map(c -> new PlannedSchedule(c.schedule(), NextClassCalculator.calculate(c.schedule(), c.slots(),
                        classroomByOccurrence, refs, states, holidays, now)))
                .filter(p -> !(p.decision() instanceof NextClassDecision.Unchanged))
                .toList();
    }

    private static Set<Long> eventIdsOf(SysacadSchedule schedule, Set<Long> currentEventIds) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Long id : new Long[] { schedule.getEventId1(), schedule.getEventId2() }) {
            if (id != null && currentEventIds.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }
}
