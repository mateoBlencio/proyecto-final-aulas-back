package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.request.AutoPreviewRequestDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.allocation.service.AutoAllocationService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.solver.model.SolverOccupancy;
import ar.edu.utn.frc.siga.solver.model.SolverEvent;
import ar.edu.utn.frc.siga.solver.model.SolverPreview;
import ar.edu.utn.frc.siga.solver.model.SolverRoom;
import ar.edu.utn.frc.siga.solver.service.SolverService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDTO;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoAllocationServiceImpl implements AutoAllocationService {

    private static final int DEFAULT_TIME_LIMIT_SECONDS = 30;

    private final AcademicEventRepository eventRepository;
    private final OccurrenceRepository occurrenceRepository;
    private final AllocationRepository allocationRepository;
    private final ClassroomService classroomService;
    private final SolverService solverService;

    @Override
    @Transactional(readOnly = true)
    public SolverPreview autoPreview(AutoPreviewRequestDto request) {
        List<RecurringEvent> events = loadRecurringEvents(request.getEventIds());
        Set<Long> ownEventIds = events.stream().map(AcademicEvent::getId).collect(Collectors.toSet());
        Map<Long, Set<LocalDate>> datesByEvent = scheduledDatesByEvent(ownEventIds);

        List<SolverEvent> solverEvents = events.stream()
                .map(e -> toSolverEvent(e, datesByEvent.getOrDefault(e.getId(), Set.of())))
                .toList();
        List<SolverRoom> classrooms = classroomService.findAllAvailable().stream()
                .map(this::toSolverRoom)
                .toList();
        List<SolverOccupancy> occupancy = buildOccupancy(events, ownEventIds);
        int timeLimit = request.getTimeLimitSeconds() != null
                ? request.getTimeLimitSeconds() : DEFAULT_TIME_LIMIT_SECONDS;

        log.info("Auto-preview: {} events, {} available classrooms, {} occupied slots",
                solverEvents.size(), classrooms.size(), occupancy.size());

        return solverService.preview(solverEvents, classrooms, occupancy, timeLimit);
    }

    private List<RecurringEvent> loadRecurringEvents(List<Long> eventIds) {
        List<AcademicEvent> found = eventRepository.findAllById(eventIds);
        if (found.size() != Set.copyOf(eventIds).size()) {
            throw ResourceNotFoundException.of("AcademicEvent", eventIds);
        }
        return found.stream().map(e -> {
            AcademicEvent unproxied = (AcademicEvent) Hibernate.unproxy(e);
            if (!(unproxied instanceof RecurringEvent recurring)) {
                throw new AllocationConflictException(
                        "auto-preview solo soporta eventos recurrentes por ahora (evento " + e.getId() + ")");
            }
            return recurring;
        }).toList();
    }

    private Map<Long, Set<LocalDate>> scheduledDatesByEvent(Set<Long> eventIds) {
        return occurrenceRepository.findByEvent_IdInAndStatus(eventIds, OccurrenceStatus.SCHEDULED).stream()
                .collect(Collectors.groupingBy(
                        o -> o.getEvent().getId(),
                        Collectors.mapping(Occurrence::getDate, Collectors.toSet())));
    }

    private SolverEvent toSolverEvent(RecurringEvent e, Set<LocalDate> dates) {
        String commissionKey = e.getCommission() != null ? e.getCommission().getCourseCode() : null;
        return new SolverEvent(String.valueOf(e.getId()), commissionKey, e.getEnrolled(),
                e.getStartTime(), e.endTime(), dates);
    }

    private SolverRoom toSolverRoom(ClassroomResponseDTO c) {
        return new SolverRoom(c.getId(), c.getCapacity(), c.getBuildingId());
    }

    private List<SolverOccupancy> buildOccupancy(List<RecurringEvent> events, Set<Long> ownEventIds) {
        LocalDate from = events.stream().map(RecurringEvent::getStartDate)
                .min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = events.stream()
                .map(e -> e.getEndDate() != null ? e.getEndDate() : e.getStartDate().plusYears(1))
                .max(Comparator.naturalOrder()).orElseThrow();

        return allocationRepository.findOccupancyBetween(from, to).stream()
                .filter(a -> !ownEventIds.contains(a.getOccurrence().getEvent().getId()))
                .map(this::toOccupancy)
                .toList();
    }

    private SolverOccupancy toOccupancy(Allocation a) {
        AcademicEvent occupant = a.getOccurrence().getEvent();
        return new SolverOccupancy(
                a.getClassroom().getId(),
                a.getOccurrence().getDate(),
                occupant.getStartTime(),
                occupant.endTime());
    }
}
