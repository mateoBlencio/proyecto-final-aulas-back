package ar.edu.utn.frc.siga.preview.service.impl;

import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.service.AllocationOccupancyService;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerEvent;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerOccupancy;
import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerRoom;
import ar.edu.utn.frc.siga.optimizer.service.OptimizerService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * evento y occurrences vía las fachadas de {@code events} (DTOs, no entidades), y corre el
 * solver sobre esos datos. Existe para que {@code PreviewServiceImpl#autoPreview} pueda
 * mantenerse sin transacción larga: la corrida del solver (hasta varios minutos) no debe
 * retener una conexión JDBC del pool (deuda B3).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class PreviewEngine {

    private final AcademicEventService academicEventService;
    private final OccurrenceService occurrenceService;
    private final ClassroomService classroomService;
    private final AllocationOccupancyService occupancyService;
    private final OptimizerService optimizerService;

    /**
     * Todo lo que compone una preview necesita, ya materializado fuera de la sesión de
     * Hibernate. {@code priorRoomByEvent} es el aula que cada evento seleccionado ya tenía
     * asignada (si tenía): garantiza que un evento previamente asignado nunca regrese a
     * {@code unresolved} — si el solver no lo ubica, conserva su aula previa.
     */
    record Inputs(List<RecurringEventResponseDto> events, Map<Long, List<LocalDate>> datesByEvent,
                  List<OptimizerRoom> rooms, List<OptimizerOccupancy> occupancy,
                  List<OccupiedSlot> databaseOccupancy, Map<Long, Integer> priorRoomByEvent) {
    }

    @Transactional(readOnly = true)
    Inputs loadInputs(Set<Long> eventIds) {
        List<RecurringEventResponseDto> events = loadRecurringEvents(eventIds);
        Map<Long, List<LocalDate>> datesByEvent = datesByEvent(eventIds);
        List<OptimizerRoom> rooms = classroomService.findAllAvailable().stream()
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

        List<OptimizerOccupancy> occupancy = databaseOccupancy.stream().map(this::toOccupancy).toList();
        return new Inputs(events, datesByEvent, rooms, occupancy, databaseOccupancy, priorRoomByEvent);
    }

    /**
     * Corre el solver: carga inputs, arma los {@link OptimizerEvent}, invoca {@link OptimizerService}.
     * NO guarda la preview — eso lo decide el caller (separar calcular de guardar).
     */
    OptimizationResult generate(Set<Long> eventIds, int timeLimitSeconds) {
        Inputs inputs = loadInputs(eventIds);
        List<OptimizerEvent> optimizerEvents = inputs.events().stream()
                .map(e -> toSolverEvent(e, inputs.datesByEvent().getOrDefault(e.id(), List.of())))
                .filter(e -> !e.occurrenceDates().isEmpty())
                .toList();
        if (optimizerEvents.isEmpty()) {
            throw new AllocationConflictException(
                    "los eventos indicados no tienen ocurrencias pendientes de asignación");
        }

        log.info("Auto-preview: {} eventos, {} aulas disponibles, {} franjas ocupadas",
                optimizerEvents.size(), inputs.rooms().size(), inputs.occupancy().size());

        return optimizerService.optimize(optimizerEvents, inputs.rooms(), inputs.occupancy(), timeLimitSeconds);
    }

    private OptimizerEvent toSolverEvent(RecurringEventResponseDto e, List<LocalDate> dates) {
        String commissionKey = e.commission() != null ? String.valueOf(e.commission().id()) : null;
        return new OptimizerEvent(String.valueOf(e.id()), commissionKey, e.enrolled(),
                e.startTime(), e.endTime(), Set.copyOf(dates));
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
     * Fechas futuras de occurrences por evento, sin filtrar por estado: "tiene aula" ya no
     * es un estado, así que re-resolver un evento ya asignado no requiere distinguirlo
     * de uno sin asignar; el filtro de fecha evita re-resolver clases ya dictadas.
     */
    private Map<Long, List<LocalDate>> datesByEvent(Set<Long> eventIds) {
        return occurrenceService.findSlotsByEvents(eventIds, LocalDate.now())
                .stream()
                .collect(Collectors.groupingBy(
                        OccurrenceSlotDto::eventId,
                        Collectors.mapping(OccurrenceSlotDto::date,
                                Collectors.collectingAndThen(Collectors.toCollection(TreeSet::new),
                                        List::copyOf))));
    }

    private OptimizerRoom toSolverRoom(ClassroomResponseDto c) {
        return new OptimizerRoom(c.id(), c.capacity(), c.buildingId());
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

        return occupancyService.findOccupancy(from, to);
    }

    private OptimizerOccupancy toOccupancy(OccupiedSlot slot) {
        return new OptimizerOccupancy(slot.classroomId(), slot.date(), slot.startTime(), slot.endTime());
    }
}
