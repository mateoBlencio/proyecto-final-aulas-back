package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.request.AutoPreviewRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.ConfirmAutoPreviewRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.PreviewAllocationDto;
import ar.edu.utn.frc.siga.allocation.dto.request.ValidateMoveRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AutoPreviewResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ConfirmAutoPreviewResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.MoveConflictDto;
import ar.edu.utn.frc.siga.allocation.dto.response.MoveConflictDto.ConflictOrigin;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceConflictDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ProposedAllocationDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ValidateMoveResponseDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.exception.ReassignConflictException;
import ar.edu.utn.frc.siga.allocation.mapper.AcademicEventComposer;
import ar.edu.utn.frc.siga.allocation.mapper.AllocationComposer;
import ar.edu.utn.frc.siga.allocation.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.allocation.service.AutoAllocationService;
import ar.edu.utn.frc.siga.allocation.service.impl.AutoAllocationDataLoader.AutoPreviewInputs;
import ar.edu.utn.frc.siga.allocation.service.impl.AutoAllocationDataLoader.DatabaseOccupancy;
import ar.edu.utn.frc.siga.solver.model.SolverAllocation;
import ar.edu.utn.frc.siga.solver.model.SolverEvent;
import ar.edu.utn.frc.siga.solver.model.SolverPreview;
import ar.edu.utn.frc.siga.solver.service.SolverService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoAllocationServiceImpl implements AutoAllocationService {

    private static final int DEFAULT_TIME_LIMIT_SECONDS = 30;

    private final AutoAllocationDataLoader dataLoader;
    private final ClassroomService classroomService;
    private final AcademicEventComposer academicEventComposer;
    private final SolverService solverService;
    private final OccurrenceRepository occurrenceRepository;
    private final AllocationRepository allocationRepository;
    private final AllocationComposer allocationComposer;

    /**
     * Sin {@code @Transactional} (deuda B3): la carga de datos vive en una transacción
     * corta propia ({@link AutoAllocationDataLoader}); el solve (hasta varios minutos) y
     * la composición final corren sin conexión JDBC retenida.
     */
    @Override
    public AutoPreviewResponseDto autoPreview(AutoPreviewRequestDto request) {
        Set<Long> eventIds = Set.copyOf(request.eventIds());
        AutoPreviewInputs inputs = dataLoader.load(eventIds);

        List<SolverEvent> solverEvents = inputs.events().stream()
                .map(e -> toSolverEvent(e, inputs.datesByEvent().getOrDefault(e.getId(), List.of())))
                .filter(e -> !e.occurrenceDates().isEmpty())
                .toList();
        if (solverEvents.isEmpty()) {
            throw new AllocationConflictException(
                    "los eventos indicados no tienen ocurrencias pendientes de asignación");
        }

        int timeLimit = request.timeLimitSeconds() != null
                ? request.timeLimitSeconds() : DEFAULT_TIME_LIMIT_SECONDS;

        log.info("Auto-preview: {} eventos, {} aulas disponibles, {} franjas ocupadas",
                solverEvents.size(), inputs.rooms().size(), inputs.occupancy().size());

        SolverPreview preview = solverService.preview(solverEvents, inputs.rooms(), inputs.occupancy(), timeLimit);
        return compose(preview, inputs.events(), inputs.datesByEvent());
    }

    @Override
    public AutoPreviewResponseDto getPreview(String previewId) {
        SolverPreview preview = solverService.getPreview(previewId);
        Set<Long> eventIds = preview.allocations().stream()
                .map(a -> Long.valueOf(a.eventId()))
                .collect(Collectors.toSet());
        AutoPreviewInputs inputs = dataLoader.load(eventIds);
        return compose(preview, inputs.events(), inputs.datesByEvent());
    }

    /**
     * Sin mutación (pura consulta): reusa una sola carga del {@link AutoAllocationDataLoader}
     * con el set completo de eventos del preview para tener fechas y horarios de todos
     * ellos (necesarios para el chequeo PREVIEW), y la ocupación de BD ya excluye esos
     * mismos eventos (sus aulas están liberadas para el solver).
     */
    @Override
    public ValidateMoveResponseDto validateMove(String previewId, ValidateMoveRequestDto request) {
        SolverPreview preview = solverService.getPreview(previewId);
        Set<Long> previewEventIds = preview.allocations().stream()
                .map(a -> Long.valueOf(a.eventId()))
                .collect(Collectors.toSet());
        validateBelongsToPreview(request, previewEventIds);

        AutoPreviewInputs inputs = dataLoader.load(previewEventIds);
        Map<Long, RecurringEvent> eventsById = inputs.events().stream()
                .collect(Collectors.toMap(AcademicEvent::getId, e -> e));

        RecurringEvent movedEvent = eventsById.get(request.eventId());
        Set<LocalDate> movedDates = Set.copyOf(inputs.datesByEvent().getOrDefault(request.eventId(), List.of()));
        LocalTime movedStart = movedEvent.getStartTime();
        LocalTime movedEnd = movedEvent.endTime();

        List<MoveConflictDto> conflicts = new ArrayList<>();
        conflicts.addAll(databaseConflicts(inputs, request.classroomId(), movedDates, movedStart, movedEnd));
        conflicts.addAll(previewConflicts(request, inputs, eventsById, movedDates, movedStart, movedEnd));

        return new ValidateMoveResponseDto(conflicts.isEmpty(), conflicts);
    }

    /**
     * Confirma atómicamente la propuesta final ajustada: TODAS las validaciones corren
     * antes de la primera escritura (preview vigente, sin duplicados, subconjunto del
     * preview, aulas existentes/disponibles, sin solapamiento nuevo contra BD ni dentro
     * del propio set). {@code source = AUTOMATIC} se estampa siempre acá adentro, nunca
     * lo decide el cliente. Invalida el preview al final: un re-confirm da 410.
     */
    @Override
    @Transactional
    public ConfirmAutoPreviewResponseDto confirm(String previewId, ConfirmAutoPreviewRequestDto request) {
        SolverPreview preview = solverService.getPreview(previewId);
        Set<Long> previewEventIds = preview.allocations().stream()
                .map(a -> Long.valueOf(a.eventId()))
                .collect(Collectors.toSet());

        validateNoDuplicateEventIds(request.allocations());
        validateAllocationsBelongToPreview(request.allocations(), previewEventIds);

        // Collectors.toMap no admite valores null (Map.merge los rechaza) y classroomId
        // puede serlo (evento sin aula propuesta) → se construye el mapa a mano.
        Map<Long, Integer> classroomByEvent = new LinkedHashMap<>();
        for (PreviewAllocationDto allocation : request.allocations()) {
            classroomByEvent.put(allocation.eventId(), allocation.classroomId());
        }
        List<Long> skippedEventIds = classroomByEvent.entrySet().stream()
                .filter(entry -> entry.getValue() == null)
                .map(Map.Entry::getKey)
                .toList();
        Set<Long> eventIdsWithClassroom = classroomByEvent.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        if (eventIdsWithClassroom.isEmpty()) {
            log.info("Confirm sin aulas propuestas: previewId={}, skipped={}", previewId, skippedEventIds.size());
            return new ConfirmAutoPreviewResponseDto(List.of(), skippedEventIds);
        }

        AutoPreviewInputs inputs = dataLoader.load(eventIdsWithClassroom);
        validateClassrooms(classroomByEvent, eventIdsWithClassroom);

        List<Occurrence> targetOccurrences = occurrenceRepository
                .findByEvent_IdInAndStatusInAndDateGreaterThanEqual(
                        eventIdsWithClassroom, List.of(OccurrenceStatus.SCHEDULED, OccurrenceStatus.ASSIGNED),
                        LocalDate.now())
                .stream()
                .filter(this::isApplicable)
                .toList();

        List<OccurrenceConflictDto> conflicts = new ArrayList<>();
        conflicts.addAll(databaseOverlapConflicts(targetOccurrences, classroomByEvent, inputs.databaseOccupancy()));
        conflicts.addAll(internalOverlapConflicts(targetOccurrences, classroomByEvent));
        if (!conflicts.isEmpty()) {
            throw new ReassignConflictException(conflicts);
        }

        List<Allocation> saved = applyAllocations(targetOccurrences, classroomByEvent);
        solverService.invalidatePreview(previewId);

        log.info("Confirm aplicado: previewId={}, applied={}, skipped={}",
                previewId, saved.size(), skippedEventIds.size());
        return new ConfirmAutoPreviewResponseDto(allocationComposer.composeAll(saved), skippedEventIds);
    }

    /** La propuesta final no puede traer el mismo evento dos veces. */
    private void validateNoDuplicateEventIds(List<PreviewAllocationDto> allocations) {
        List<Long> eventIds = allocations.stream().map(PreviewAllocationDto::eventId).toList();
        if (new HashSet<>(eventIds).size() != eventIds.size()) {
            throw new AllocationConflictException("La propuesta final tiene eventos duplicados.");
        }
    }

    /** Todo evento de la propuesta final debe pertenecer al preview que se está confirmando. */
    private void validateAllocationsBelongToPreview(List<PreviewAllocationDto> allocations, Set<Long> previewEventIds) {
        Set<Long> foreign = allocations.stream()
                .map(PreviewAllocationDto::eventId)
                .filter(id -> !previewEventIds.contains(id))
                .collect(Collectors.toSet());
        if (!foreign.isEmpty()) {
            throw new AllocationConflictException(
                    "los eventos " + foreign + " no pertenecen al preview indicado");
        }
    }

    /** UN batch de aulas: inexistente o no disponible corta con 409 antes de escribir nada. */
    private void validateClassrooms(Map<Long, Integer> classroomByEvent, Set<Long> eventIdsWithClassroom) {
        Set<Integer> classroomIds = eventIdsWithClassroom.stream()
                .map(classroomByEvent::get)
                .collect(Collectors.toSet());
        Map<Integer, ClassroomResponseDto> classroomsById = classroomService.findByIds(classroomIds).stream()
                .collect(Collectors.toMap(ClassroomResponseDto::id, c -> c));
        for (Integer classroomId : classroomIds) {
            ClassroomResponseDto classroom = classroomsById.get(classroomId);
            if (classroom == null || !Boolean.TRUE.equals(classroom.available())) {
                throw new AllocationConflictException("El aula " + classroomId + " no existe o no está disponible.");
            }
        }
    }

    /** Ocurrencia asignable: no pasada, ni CANCELLED/SUSPENDED (patrón {@code allocateToOccurrences}). */
    private boolean isApplicable(Occurrence occurrence) {
        return !occurrence.isPast()
                && occurrence.getStatus() != OccurrenceStatus.CANCELLED
                && occurrence.getStatus() != OccurrenceStatus.SUSPENDED;
    }

    /**
     * Conflictos contra asignaciones firmes de BD: {@code databaseOccupancy} ya excluye los
     * eventos del propio set (sus aulas quedaron liberadas), así que solo puede chocar
     * contra ocupación de eventos ajenos al set que se está confirmando.
     */
    private List<OccurrenceConflictDto> databaseOverlapConflicts(List<Occurrence> targetOccurrences,
            Map<Long, Integer> classroomByEvent, List<DatabaseOccupancy> databaseOccupancy) {
        List<OccurrenceConflictDto> conflicts = new ArrayList<>();
        for (Occurrence occurrence : targetOccurrences) {
            Integer classroomId = classroomByEvent.get(occurrence.getEvent().getId());
            LocalTime start = occurrence.startTime();
            LocalTime end = occurrence.endTime();
            for (DatabaseOccupancy occupied : databaseOccupancy) {
                if (!classroomId.equals(occupied.classroomId())) continue;
                if (!occurrence.getDate().equals(occupied.date())) continue;
                if (!overlaps(start, end, occupied.startTime(), occupied.endTime())) continue;
                conflicts.add(new OccurrenceConflictDto(occurrence.getId(), occurrence.getDate(), start, end,
                        classroomId, occupied.eventId(), occupied.allocationId()));
            }
        }
        return conflicts;
    }

    /**
     * Conflictos entre los propios ítems del set final: dos eventos distintos cayendo en
     * la misma aula/fecha con franjas que se pisan. Nada persiste todavía → no hay
     * asignación real involucrada en el choque, {@code conflictingAllocationId} va null.
     */
    private List<OccurrenceConflictDto> internalOverlapConflicts(List<Occurrence> targetOccurrences,
            Map<Long, Integer> classroomByEvent) {
        Map<String, List<Occurrence>> byClassroomAndDate = targetOccurrences.stream()
                .collect(Collectors.groupingBy(
                        o -> classroomByEvent.get(o.getEvent().getId()) + "|" + o.getDate()));

        List<OccurrenceConflictDto> conflicts = new ArrayList<>();
        for (List<Occurrence> group : byClassroomAndDate.values()) {
            if (group.size() < 2) continue;
            for (int i = 0; i < group.size(); i++) {
                Occurrence a = group.get(i);
                for (int j = i + 1; j < group.size(); j++) {
                    Occurrence b = group.get(j);
                    if (a.getEvent().getId().equals(b.getEvent().getId())) continue;
                    if (!overlaps(a.startTime(), a.endTime(), b.startTime(), b.endTime())) continue;
                    conflicts.add(new OccurrenceConflictDto(a.getId(), a.getDate(), a.startTime(), a.endTime(),
                            classroomByEvent.get(a.getEvent().getId()), b.getEvent().getId(), null));
                }
            }
        }
        return conflicts;
    }

    /**
     * Aplica la propuesta final: actualiza la allocation existente de cada ocurrencia
     * (aula nueva + {@code source = AUTOMATIC}) o crea una si no había, y la ocurrencia
     * pasa a ASSIGNED. Batch {@code findByOccurrence_IdIn} para evitar N+1.
     */
    private List<Allocation> applyAllocations(List<Occurrence> targetOccurrences, Map<Long, Integer> classroomByEvent) {
        List<Long> occurrenceIds = targetOccurrences.stream().map(Occurrence::getId).toList();
        Map<Long, Allocation> existingByOccurrenceId = allocationRepository.findByOccurrence_IdIn(occurrenceIds).stream()
                .collect(Collectors.toMap(a -> a.getOccurrence().getId(), a -> a));

        List<Allocation> saved = new ArrayList<>();
        for (Occurrence occurrence : targetOccurrences) {
            Integer classroomId = classroomByEvent.get(occurrence.getEvent().getId());
            Allocation allocation = existingByOccurrenceId.get(occurrence.getId());
            if (allocation != null) {
                allocation.setClassroomId(classroomId);
                allocation.setSource(AllocationSource.AUTOMATIC);
            } else {
                allocation = Allocation.builder()
                        .occurrence(occurrence)
                        .classroomId(classroomId)
                        .source(AllocationSource.AUTOMATIC)
                        .createdAt(LocalDateTime.now())
                        .build();
            }
            saved.add(allocationRepository.save(allocation));

            occurrence.setStatus(OccurrenceStatus.ASSIGNED);
            occurrenceRepository.save(occurrence);
        }
        return saved;
    }

    /** {@code eventId} y todos los {@code currentAllocations} deben pertenecer al preview vigente. */
    private void validateBelongsToPreview(ValidateMoveRequestDto request, Set<Long> previewEventIds) {
        if (!previewEventIds.contains(request.eventId())) {
            throw new AllocationConflictException(
                    "el evento " + request.eventId() + " no pertenece al preview indicado");
        }
        Set<Long> foreign = request.currentAllocations().stream()
                .map(PreviewAllocationDto::eventId)
                .filter(id -> !previewEventIds.contains(id))
                .collect(Collectors.toSet());
        if (!foreign.isEmpty()) {
            throw new AllocationConflictException(
                    "los eventos " + foreign + " de currentAllocations no pertenecen al preview indicado");
        }
    }

    /**
     * Conflictos contra asignaciones firmes de BD: {@code inputs.databaseOccupancy()} ya
     * excluye los eventos del preview (sus aulas quedaron liberadas), así que solo puede
     * chocar contra ocupación de eventos ajenos al preview.
     */
    private List<MoveConflictDto> databaseConflicts(AutoPreviewInputs inputs, Integer destination,
            Set<LocalDate> movedDates, LocalTime movedStart, LocalTime movedEnd) {
        List<MoveConflictDto> conflicts = new ArrayList<>();
        for (DatabaseOccupancy occupancy : inputs.databaseOccupancy()) {
            if (!destination.equals(occupancy.classroomId())) continue;
            if (!movedDates.contains(occupancy.date())) continue;
            if (!overlaps(movedStart, movedEnd, occupancy.startTime(), occupancy.endTime())) continue;
            conflicts.add(new MoveConflictDto(occupancy.date(), occupancy.startTime(), occupancy.endTime(),
                    destination, occupancy.eventId(), ConflictOrigin.DATABASE));
        }
        return conflicts;
    }

    /**
     * Conflictos contra el resto de la propuesta ajustada que viaja en el request: otros
     * ítems de {@code currentAllocations} (excluido el propio evento movido) que también
     * apuntan al aula destino, comparando fechas compartidas y franja horaria.
     */
    private List<MoveConflictDto> previewConflicts(ValidateMoveRequestDto request, AutoPreviewInputs inputs,
            Map<Long, RecurringEvent> eventsById, Set<LocalDate> movedDates, LocalTime movedStart, LocalTime movedEnd) {
        List<MoveConflictDto> conflicts = new ArrayList<>();
        for (PreviewAllocationDto allocation : request.currentAllocations()) {
            if (allocation.eventId().equals(request.eventId())) continue;
            if (!request.classroomId().equals(allocation.classroomId())) continue;

            RecurringEvent other = eventsById.get(allocation.eventId());
            if (other == null) continue;
            LocalTime otherStart = other.getStartTime();
            LocalTime otherEnd = other.endTime();
            if (!overlaps(movedStart, movedEnd, otherStart, otherEnd)) continue;

            for (LocalDate date : inputs.datesByEvent().getOrDefault(allocation.eventId(), List.of())) {
                if (!movedDates.contains(date)) continue;
                conflicts.add(new MoveConflictDto(date, otherStart, otherEnd, request.classroomId(),
                        allocation.eventId(), ConflictOrigin.PREVIEW));
            }
        }
        return conflicts;
    }

    /** Barrido de franjas horarias reusable (Fase 4): fin == inicio no es solapamiento. */
    private boolean overlaps(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        return start1.isBefore(end2) && start2.isBefore(end1);
    }

    private SolverEvent toSolverEvent(RecurringEvent e, List<LocalDate> dates) {
        String commissionKey = e.getCommissionId() != null ? String.valueOf(e.getCommissionId()) : null;
        return new SolverEvent(String.valueOf(e.getId()), commissionKey, e.getEnrolled(),
                e.getStartTime(), e.endTime(), Set.copyOf(dates));
    }

    /**
     * Compone el DTO propio de allocation a partir de la preview cruda del solver:
     * separa resueltos (con aula) de {@code unresolved} (classroomId null, revisión
     * manual), y resuelve evento y aula en un solo batch cada uno.
     */
    private AutoPreviewResponseDto compose(SolverPreview preview, List<RecurringEvent> events,
                                            Map<Long, List<LocalDate>> datesByEvent) {
        Map<Long, RecurringEvent> eventsById = events.stream()
                .collect(Collectors.toMap(AcademicEvent::getId, e -> e));

        List<SolverAllocation> resolved = new ArrayList<>();
        List<SolverAllocation> unresolved = new ArrayList<>();
        for (SolverAllocation allocation : preview.allocations()) {
            if (allocation.classroomId() != null) {
                resolved.add(allocation);
            } else {
                unresolved.add(allocation);
            }
        }

        List<RecurringEvent> referencedEvents = preview.allocations().stream()
                .map(a -> eventsById.get(Long.valueOf(a.eventId())))
                .filter(Objects::nonNull)
                .toList();
        Map<Long, AcademicEventResponseDto> eventDtoById = composeEventsById(referencedEvents);

        Set<Integer> classroomIds = resolved.stream()
                .map(SolverAllocation::classroomId)
                .collect(Collectors.toSet());
        Map<Integer, ClassroomResponseDto> classroomDtoById = classroomService.findByIds(classroomIds).stream()
                .collect(Collectors.toMap(ClassroomResponseDto::id, c -> c));

        List<ProposedAllocationDto> allocations = resolved.stream()
                .map(a -> toProposedAllocationDto(a, eventDtoById, datesByEvent, classroomDtoById.get(a.classroomId())))
                .toList();
        List<ProposedAllocationDto> unresolvedDtos = unresolved.stream()
                .map(a -> toProposedAllocationDto(a, eventDtoById, datesByEvent, null))
                .toList();

        return new AutoPreviewResponseDto(preview.previewId(), allocations, unresolvedDtos);
    }

    /** Composición por lote de eventos ajenos, indexada por id para lookup O(1). */
    private Map<Long, AcademicEventResponseDto> composeEventsById(List<RecurringEvent> events) {
        List<AcademicEventResponseDto> composed = academicEventComposer.compose(events);
        Map<Long, AcademicEventResponseDto> byId = new LinkedHashMap<>();
        for (int i = 0; i < events.size(); i++) {
            byId.put(events.get(i).getId(), composed.get(i));
        }
        return byId;
    }

    private ProposedAllocationDto toProposedAllocationDto(SolverAllocation allocation,
            Map<Long, AcademicEventResponseDto> eventDtoById, Map<Long, List<LocalDate>> datesByEvent,
            ClassroomResponseDto classroom) {
        Long eventId = Long.valueOf(allocation.eventId());
        return new ProposedAllocationDto(
                eventDtoById.get(eventId), datesByEvent.getOrDefault(eventId, List.of()), classroom);
    }
}
