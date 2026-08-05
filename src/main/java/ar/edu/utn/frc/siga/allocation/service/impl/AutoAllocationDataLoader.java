package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator.OccupiedSlot;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.solver.model.SolverOccupancy;
import ar.edu.utn.frc.siga.solver.model.SolverRoom;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Carga todo lo que necesita el auto-preview en una única transacción corta, resolviendo
 * evento y occurrences vía las fachadas de {@code events} (DTOs, no entidades). Existe para
 * que {@code AutoAllocationServiceImpl#autoPreview} pueda dejar de ser transaccional: la
 * corrida del solver (hasta varios minutos) no debe retener una conexión JDBC del pool
 * (deuda B3).
 */
@Component
@RequiredArgsConstructor
class AutoAllocationDataLoader {

    private final AcademicEventService academicEventService;
    private final OccurrenceService occurrenceService;
    private final AllocationRepository allocationRepository;
    private final ClassroomService classroomService;

    /**
     * Todo lo que el auto-preview necesita, ya materializado fuera de la sesión de Hibernate.
     * {@code priorRoomByEvent} es el aula que cada evento seleccionado ya tenía asignada
     * (si tenía): garantiza que un evento previamente asignado nunca regrese a
     * {@code unresolved} — si el solver no lo ubica, conserva su aula previa.
     */
    record AutoPreviewInputs(List<RecurringEventResponseDto> events, Map<Long, List<LocalDate>> datesByEvent,
                              List<SolverRoom> rooms, List<SolverOccupancy> occupancy,
                              List<OccupiedSlot> databaseOccupancy,
                              Map<Long, Integer> priorRoomByEvent) {
    }

    @Transactional(readOnly = true)
    AutoPreviewInputs load(Set<Long> eventIds) {
        List<RecurringEventResponseDto> events = loadRecurringEvents(eventIds);
        Map<Long, List<LocalDate>> datesByEvent = datesByEvent(eventIds);
        List<SolverRoom> rooms = classroomService.findAllAvailable().stream()
                .map(this::toSolverRoom)
                .toList();

        List<OccupiedSlot> occupancyInRange = loadOccupancyInRange(events);
        // Ocupación ajena (pinned): excluye los eventos seleccionados → sus aulas quedan libres.
        List<OccupiedSlot> databaseOccupancy = occupancyInRange.stream()
                .filter(o -> !eventIds.contains(o.eventId()))
                .toList();
        // Aula previa de cada evento seleccionado que ya estaba asignado (floor de no-regresión).
        Map<Long, Integer> priorRoomByEvent = occupancyInRange.stream()
                .filter(o -> eventIds.contains(o.eventId()))
                .collect(Collectors.toMap(OccupiedSlot::eventId, OccupiedSlot::classroomId, (x, y) -> x));

        List<SolverOccupancy> occupancy = databaseOccupancy.stream().map(this::toOccupancy).toList();
        return new AutoPreviewInputs(events, datesByEvent, rooms, occupancy, databaseOccupancy, priorRoomByEvent);
    }

    private List<RecurringEventResponseDto> loadRecurringEvents(Set<Long> eventIds) {
        List<AcademicEventResponseDto> found = academicEventService.findByIds(eventIds);
        if (found.size() != eventIds.size()) {
            throw ResourceNotFoundException.of("AcademicEvent", eventIds);
        }
        return found.stream().map(e -> {
            if (!(e instanceof RecurringEventResponseDto recurring)) {
                throw new AllocationConflictException(
                        "auto-preview solo soporta eventos recurrentes por ahora (evento " + e.id() + ")");
            }
            return recurring;
        }).toList();
    }

    /**
     * Fechas futuras de occurrences SCHEDULED o ASSIGNED por evento: incluir ASSIGNED
     * trae las fechas de eventos ya asignados que se quieren re-resolver; el filtro
     * de fecha evita re-resolver clases ya dictadas.
     */
    private Map<Long, List<LocalDate>> datesByEvent(Set<Long> eventIds) {
        return occurrenceService.findSlotsByEventsAndStatuses(
                        eventIds, List.of(OccurrenceStatus.SCHEDULED, OccurrenceStatus.ASSIGNED), LocalDate.now())
                .stream()
                .collect(Collectors.groupingBy(
                        OccurrenceSlotDto::eventId,
                        Collectors.mapping(OccurrenceSlotDto::date,
                                Collectors.collectingAndThen(Collectors.toCollection(TreeSet::new),
                                        List::copyOf))));
    }

    private SolverRoom toSolverRoom(ClassroomResponseDto c) {
        return new SolverRoom(c.id(), c.capacity(), c.buildingId());
    }

    /**
     * Ocupación existente (ASSIGNED) en el rango de fechas de los eventos seleccionados, sin
     * filtrar por evento: quien llama separa la ajena (pinned) de la de los propios eventos
     * (que da el aula previa para el floor de no-regresión).
     */
    private List<OccupiedSlot> loadOccupancyInRange(List<RecurringEventResponseDto> events) {
        if (events.isEmpty()) {
            return List.of();
        }
        LocalDate from = events.stream().map(RecurringEventResponseDto::startDate)
                .min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = events.stream()
                .map(e -> e.endDate() != null ? e.endDate() : e.startDate().plusYears(1))
                .max(Comparator.naturalOrder()).orElseThrow();

        Map<Long, OccurrenceSlotDto> slotByOccurrenceId = Maps.byId(
                occurrenceService.findSlotsByStatusBetween(OccurrenceStatus.ASSIGNED, from, to),
                OccurrenceSlotDto::occurrenceId);
        return allocationRepository.findByOccurrenceIdIn(slotByOccurrenceId.keySet()).stream()
                .map(a -> OccupiedSlot.from(a, slotByOccurrenceId.get(a.getOccurrenceId())))
                .toList();
    }

    private SolverOccupancy toOccupancy(OccupiedSlot slot) {
        return new SolverOccupancy(slot.classroomId(), slot.date(), slot.startTime(), slot.endTime());
    }
}
