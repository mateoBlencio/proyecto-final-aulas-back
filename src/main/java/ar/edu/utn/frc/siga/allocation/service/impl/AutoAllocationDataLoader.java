package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.solver.model.SolverOccupancy;
import ar.edu.utn.frc.siga.solver.model.SolverRoom;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Carga todo lo que necesita el auto-preview en una única transacción corta y lo
 * devuelve materializado (entidades des-proxeadas con {@link Hibernate#unproxy}, con
 * OSIV off un proxy lazy fuera de transacción explota). Existe para que
 * {@code AutoAllocationServiceImpl#autoPreview} pueda dejar de ser transaccional: la
 * corrida del solver (hasta varios minutos) no debe retener una conexión JDBC del pool
 * (deuda B3).
 */
@Component
@RequiredArgsConstructor
class AutoAllocationDataLoader {

    private final AcademicEventRepository eventRepository;
    private final OccurrenceRepository occurrenceRepository;
    private final AllocationRepository allocationRepository;
    private final ClassroomService classroomService;

    /** Todo lo que el auto-preview necesita, ya materializado fuera de la sesión de Hibernate. */
    record AutoPreviewInputs(List<RecurringEvent> events, Map<Long, List<LocalDate>> datesByEvent,
                              List<SolverRoom> rooms, List<SolverOccupancy> occupancy,
                              List<DatabaseOccupancy> databaseOccupancy) {
    }

    /**
     * Ocupación de BD con el id del evento ocupante — {@link SolverOccupancy} no lo trae
     * (el solver solo necesita la franja bloqueada, no quién la ocupa). Lo necesita
     * validate-move para reportar {@code conflictingEventId} en un conflicto DATABASE.
     */
    record DatabaseOccupancy(Integer classroomId, LocalDate date, LocalTime startTime, LocalTime endTime,
                              Long eventId) {
    }

    @Transactional(readOnly = true)
    AutoPreviewInputs load(Set<Long> eventIds) {
        List<RecurringEvent> events = loadRecurringEvents(eventIds);
        Map<Long, List<LocalDate>> datesByEvent = datesByEvent(eventIds);
        List<SolverRoom> rooms = classroomService.findAllAvailable().stream()
                .map(this::toSolverRoom)
                .toList();
        List<Allocation> databaseAllocations = loadDatabaseAllocations(events, eventIds);
        List<SolverOccupancy> occupancy = databaseAllocations.stream().map(this::toOccupancy).toList();
        List<DatabaseOccupancy> databaseOccupancy = databaseAllocations.stream()
                .map(this::toDatabaseOccupancy)
                .toList();
        return new AutoPreviewInputs(events, datesByEvent, rooms, occupancy, databaseOccupancy);
    }

    private List<RecurringEvent> loadRecurringEvents(Set<Long> eventIds) {
        List<AcademicEvent> found = eventRepository.findAllById(eventIds);
        if (found.size() != eventIds.size()) {
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

    /**
     * Fechas futuras de occurrences SCHEDULED o ASSIGNED por evento: incluir ASSIGNED
     * trae las fechas de eventos ya asignados que se quieren re-resolver (D2); el filtro
     * de fecha evita re-resolver clases ya dictadas.
     */
    private Map<Long, List<LocalDate>> datesByEvent(Set<Long> eventIds) {
        return occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(
                        eventIds, List.of(OccurrenceStatus.SCHEDULED, OccurrenceStatus.ASSIGNED), LocalDate.now())
                .stream()
                .collect(Collectors.groupingBy(
                        o -> o.getEvent().getId(),
                        Collectors.mapping(Occurrence::getDate,
                                Collectors.collectingAndThen(Collectors.toCollection(TreeSet::new),
                                        List::copyOf))));
    }

    private SolverRoom toSolverRoom(ClassroomResponseDto c) {
        return new SolverRoom(c.id(), c.capacity(), c.buildingId());
    }

    /**
     * Ocupación existente en el rango de los eventos seleccionados, EXCLUYENDO las
     * allocations cuyo evento está en {@code selectedEventIds}: esas aulas quedan libres
     * para que el solver pueda reasignarlas (D2). El resto sigue pinned. Se devuelve la
     * entidad completa (no un record del solver) para poder derivar tanto la ocupación
     * pinned del solver como el detalle con eventId que necesita validate-move.
     */
    private List<Allocation> loadDatabaseAllocations(List<RecurringEvent> events, Set<Long> selectedEventIds) {
        if (events.isEmpty()) {
            return List.of();
        }
        LocalDate from = events.stream().map(RecurringEvent::getStartDate)
                .min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = events.stream()
                .map(e -> e.getEndDate() != null ? e.getEndDate() : e.getStartDate().plusYears(1))
                .max(Comparator.naturalOrder()).orElseThrow();

        return allocationRepository.findOccupancyBetween(from, to, OccurrenceStatus.ASSIGNED).stream()
                .filter(a -> !selectedEventIds.contains(a.getOccurrence().getEvent().getId()))
                .toList();
    }

    private SolverOccupancy toOccupancy(Allocation a) {
        AcademicEvent occupant = a.getOccurrence().getEvent();
        return new SolverOccupancy(
                a.getClassroomId(),
                a.getOccurrence().getDate(),
                occupant.getStartTime(),
                occupant.endTime());
    }

    /** Igual que {@link #toOccupancy} pero conservando el id del evento ocupante. */
    private DatabaseOccupancy toDatabaseOccupancy(Allocation a) {
        AcademicEvent occupant = a.getOccurrence().getEvent();
        return new DatabaseOccupancy(
                a.getClassroomId(),
                a.getOccurrence().getDate(),
                occupant.getStartTime(),
                occupant.endTime(),
                occupant.getId());
    }
}
