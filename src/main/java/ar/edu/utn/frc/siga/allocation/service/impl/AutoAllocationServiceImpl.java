package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.request.AutoPreviewRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.ConfirmAutoPreviewRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.PreviewAllocationDto;
import ar.edu.utn.frc.siga.allocation.dto.request.ValidateMoveRequestDto;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AutoPreviewResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ConfirmAutoPreviewResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.MoveConflictDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ProposedAllocationDto;
import ar.edu.utn.frc.siga.allocation.dto.response.UnresolvedAllocationDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ValidateMoveResponseDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.allocation.mapper.AllocationComposer;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.allocation.service.AllocationProblemService;
import ar.edu.utn.frc.siga.allocation.service.AutoAllocationService;
import ar.edu.utn.frc.siga.allocation.service.impl.AutoAllocationDataLoader.AutoPreviewInputs;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator.AllocationCandidate;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator.OccupiedSlot;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator.ResolvedProposal;
import ar.edu.utn.frc.siga.common.exception.InvalidSelectionException;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.solver.model.SolverAllocation;
import ar.edu.utn.frc.siga.solver.model.SolverEvent;
import ar.edu.utn.frc.siga.solver.model.SolverPreview;
import ar.edu.utn.frc.siga.solver.model.SolverRoom;
import ar.edu.utn.frc.siga.solver.service.SolverService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Arma los modelos del solver, delega la optimización y compone/valida el resultado (preview, validate-move y confirm). */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoAllocationServiceImpl implements AutoAllocationService {

    private static final int DEFAULT_TIME_LIMIT_SECONDS = 30;

    private final AutoAllocationDataLoader dataLoader;
    private final ClassroomService classroomService;
    private final SolverService solverService;
    private final OccurrenceService occurrenceService;
    private final AllocationComposer allocationComposer;
    private final AllocationValidator validator;
    private final AllocationWriter writer;
    private final AllocationProblemService allocationProblemService;

    /** Sin {@code @Transactional}: ver {@link AutoAllocationDataLoader} para el motivo. */
    @Override
    public AutoPreviewResponseDto autoPreview(AutoPreviewRequestDto request) {
        Set<Long> eventIds = resolveEventIds(request);
        AutoPreviewInputs inputs = dataLoader.load(eventIds);

        List<SolverEvent> solverEvents = inputs.events().stream()
                .map(e -> toSolverEvent(e, inputs.datesByEvent().getOrDefault(e.id(), List.of())))
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
        return compose(preview, inputs.events(), inputs.datesByEvent(), inputs.priorRoomByEvent(),
                inputs.rooms(), inputs.databaseOccupancy());
    }

    @Override
    public AutoPreviewResponseDto getPreview(String previewId) {
        SolverPreview preview = solverService.getPreview(previewId);
        Set<Long> eventIds = preview.allocations().stream()
                .map(a -> Long.valueOf(a.eventId()))
                .collect(Collectors.toSet());
        AutoPreviewInputs inputs = dataLoader.load(eventIds);
        return compose(preview, inputs.events(), inputs.datesByEvent(), inputs.priorRoomByEvent(),
                inputs.rooms(), inputs.databaseOccupancy());
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
        validator.validateBelongsToPreview(request, previewEventIds);

        AutoPreviewInputs inputs = dataLoader.load(previewEventIds);
        Map<Long, RecurringEventResponseDto> eventsById = Maps.byId(inputs.events(), RecurringEventResponseDto::id);

        RecurringEventResponseDto movedEvent = eventsById.get(request.eventId());
        Set<LocalDate> movedDates = Set.copyOf(inputs.datesByEvent().getOrDefault(request.eventId(), List.of()));
        LocalTime movedStart = movedEvent.startTime();
        LocalTime movedEnd = movedEvent.endTime();

        List<MoveConflictDto> conflicts = new ArrayList<>();
        conflicts.addAll(validator.moveDatabaseConflicts(request.classroomId(), movedDates, movedStart, movedEnd, inputs.databaseOccupancy()));
        conflicts.addAll(validator.movePreviewConflicts(request, eventsById, inputs.datesByEvent(), movedDates, movedStart, movedEnd));

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

        validator.validateNoDuplicateEventIds(request.allocations());
        validator.validateAllocationsBelongToPreview(request.allocations(), previewEventIds);

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
        Set<Integer> classroomIds = eventIdsWithClassroom.stream().map(classroomByEvent::get).collect(Collectors.toSet());
        validator.validateClassroomsAvailable(classroomIds);

        List<OccurrenceSlotDto> targetOccurrences = occurrenceService
                .findSlotsByEventsAndStatuses(
                        eventIdsWithClassroom, List.of(OccurrenceStatus.SCHEDULED, OccurrenceStatus.ASSIGNED),
                        LocalDate.now())
                .stream()
                .filter(validator::isApplicable)
                .toList();

        List<AllocationCandidate> candidates = targetOccurrences.stream()
                .map(o -> new AllocationCandidate(o, classroomByEvent.get(o.eventId())))
                .toList();
        validator.validateNoOverlap(candidates, inputs.databaseOccupancy());

        // Aula distinta por evento, pero una sola pasada de escritura (una sola query de
        // asignaciones existentes) en vez de una por evento: evita N+1 con muchos eventos.
        List<Allocation> saved = writer.apply(targetOccurrences,
                o -> classroomByEvent.get(o.eventId()), null, AllocationSource.AUTOMATIC, true);
        solverService.invalidatePreview(previewId);

        log.info("Confirm aplicado: previewId={}, applied={}, skipped={}",
                previewId, saved.size(), skippedEventIds.size());
        return new ConfirmAutoPreviewResponseDto(allocationComposer.composeAll(saved), skippedEventIds);
    }

    /**
     * Dos modos excluyentes: {@code eventIds} explícito, o {@code selectAll=true} para
     * resolver todos los eventos sin aula ({@link AllocationProblemService#resolveAllUnassignedEventIds})
     * descontando {@code excludedIds}. Ninguno o ambos a la vez es un request inválido.
     */
    private Set<Long> resolveEventIds(AutoPreviewRequestDto request) {
        boolean selectAll = Boolean.TRUE.equals(request.selectAll());
        boolean hasExplicitIds = request.eventIds() != null && !request.eventIds().isEmpty();

        if (selectAll == hasExplicitIds) {
            throw new InvalidSelectionException(
                    "Debe indicar eventIds o selectAll=true, pero no ambos ni ninguno");
        }
        if (!selectAll) {
            return Set.copyOf(request.eventIds());
        }

        Set<Long> excludedIds = request.excludedIds() != null ? Set.copyOf(request.excludedIds()) : Set.of();
        return allocationProblemService.resolveAllUnassignedEventIds().stream()
                .filter(id -> !excludedIds.contains(id))
                .collect(Collectors.toSet());
    }

    private SolverEvent toSolverEvent(RecurringEventResponseDto e, List<LocalDate> dates) {
        String commissionKey = e.commission() != null ? String.valueOf(e.commission().id()) : null;
        return new SolverEvent(String.valueOf(e.id()), commissionKey, e.enrolled(),
                e.startTime(), e.endTime(), Set.copyOf(dates));
    }

    /**
     * Compone el DTO propio de allocation a partir de la preview cruda del solver: separa
     * resueltos (con aula) de {@code unresolved} (sin aula, revisión manual), y resuelve
     * evento y aula en un solo batch cada uno. Floor de no-regresión: un evento sin aula del
     * solver que YA estaba asignado ({@code priorRoomByEvent}) conserva esa aula previa y
     * queda en resueltos; sólo los eventos sin aula previa caen en {@code unresolved}, con los
     * conflictos que explican por qué ninguna aula candidata ({@code rooms}) le sirvió, contra
     * el estado final: ocupación firme de BD ({@code databaseOccupancy}) + los propios resueltos.
     */
    private AutoPreviewResponseDto compose(SolverPreview preview, List<RecurringEventResponseDto> events,
                                            Map<Long, List<LocalDate>> datesByEvent,
                                            Map<Long, Integer> priorRoomByEvent,
                                            List<SolverRoom> rooms, List<OccupiedSlot> databaseOccupancy) {
        Map<Long, RecurringEventResponseDto> eventsById = Maps.byId(events, RecurringEventResponseDto::id);

        List<SolverAllocation> resolved = new ArrayList<>();
        List<SolverAllocation> unresolved = new ArrayList<>();
        Map<String, Integer> effectiveRoomByEventId = new LinkedHashMap<>();
        for (SolverAllocation allocation : preview.allocations()) {
            Integer classroomId = allocation.classroomId();
            if (classroomId == null) {
                classroomId = priorRoomByEvent.get(Long.valueOf(allocation.eventId()));
            }
            if (classroomId != null) {
                resolved.add(allocation);
                effectiveRoomByEventId.put(allocation.eventId(), classroomId);
            } else {
                unresolved.add(allocation);
            }
        }

        List<AcademicEventResponseDto> referencedEvents = preview.allocations().stream()
                .<AcademicEventResponseDto>map(a -> eventsById.get(Long.valueOf(a.eventId())))
                .filter(Objects::nonNull)
                .toList();
        Map<Long, AcademicEventResponseDto> eventDtoById = Maps.byId(referencedEvents, AcademicEventResponseDto::id);

        Set<Integer> classroomIds = Set.copyOf(effectiveRoomByEventId.values());
        Map<Integer, ClassroomResponseDto> classroomDtoById = Maps.byId(classroomService.findByIds(classroomIds), ClassroomResponseDto::id);

        List<ProposedAllocationDto> allocations = resolved.stream()
                .map(a -> toProposedAllocationDto(a, eventDtoById, datesByEvent,
                        classroomDtoById.get(effectiveRoomByEventId.get(a.eventId())), priorRoomByEvent))
                .toList();

        Set<Integer> candidateRoomIds = rooms.stream().map(SolverRoom::id).collect(Collectors.toSet());
        List<ResolvedProposal> resolvedProposals = buildResolvedProposals(effectiveRoomByEventId, eventsById, datesByEvent);
        List<UnresolvedAllocationDto> unresolvedDtos = unresolved.stream()
                .map(a -> toUnresolvedAllocationDto(a, eventDtoById, datesByEvent, eventsById,
                        candidateRoomIds, databaseOccupancy, resolvedProposals))
                .toList();

        return new AutoPreviewResponseDto(preview.previewId(), allocations, unresolvedDtos);
    }

    /** Las propuestas ya resueltas del preview, en la forma que espera {@code validator.unresolvedConflicts}. */
    private List<ResolvedProposal> buildResolvedProposals(Map<String, Integer> effectiveRoomByEventId,
            Map<Long, RecurringEventResponseDto> eventsById, Map<Long, List<LocalDate>> datesByEvent) {
        List<ResolvedProposal> proposals = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : effectiveRoomByEventId.entrySet()) {
            Long eventId = Long.valueOf(entry.getKey());
            RecurringEventResponseDto event = eventsById.get(eventId);
            if (event == null) continue;
            proposals.add(new ResolvedProposal(eventId, entry.getValue(),
                    datesByEvent.getOrDefault(eventId, List.of()), event.startTime(), event.endTime()));
        }
        return proposals;
    }

    private ProposedAllocationDto toProposedAllocationDto(SolverAllocation allocation,
            Map<Long, AcademicEventResponseDto> eventDtoById, Map<Long, List<LocalDate>> datesByEvent,
            ClassroomResponseDto classroom, Map<Long, Integer> priorRoomByEvent) {
        Long eventId = Long.valueOf(allocation.eventId());
        AcademicEventResponseDto event = eventDtoById.get(eventId);
        boolean unchanged = classroom != null && Objects.equals(classroom.id(), priorRoomByEvent.get(eventId));
        return new ProposedAllocationDto(
                event, datesByEvent.getOrDefault(eventId, List.of()), classroom, overcrowdedBy(event, classroom),
                unchanged);
    }

    /**
     * Fila {@code unresolved}: evento sin aula + los conflictos que explican por qué ninguna
     * aula candidata sirvió (delegado en {@code validator.unresolvedConflicts}). Sin horario
     * del evento (evento borrado entre el solve y la composición, caso extremo) no hay franja
     * contra la que comparar y viaja sin conflictos.
     */
    private UnresolvedAllocationDto toUnresolvedAllocationDto(SolverAllocation allocation,
            Map<Long, AcademicEventResponseDto> eventDtoById, Map<Long, List<LocalDate>> datesByEvent,
            Map<Long, RecurringEventResponseDto> eventsById, Set<Integer> candidateRoomIds,
            List<OccupiedSlot> databaseOccupancy, List<ResolvedProposal> resolvedProposals) {
        Long eventId = Long.valueOf(allocation.eventId());
        AcademicEventResponseDto event = eventDtoById.get(eventId);
        List<LocalDate> dates = datesByEvent.getOrDefault(eventId, List.of());
        RecurringEventResponseDto recurringEvent = eventsById.get(eventId);
        List<MoveConflictDto> conflicts = recurringEvent == null ? List.of()
                : validator.unresolvedConflicts(candidateRoomIds, Set.copyOf(dates),
                        recurringEvent.startTime(), recurringEvent.endTime(), databaseOccupancy, resolvedProposals);
        return new UnresolvedAllocationDto(event, dates, conflicts);
    }

    /** Alumnos que exceden la capacidad del aula propuesta (0 si entran, o si la fila es unresolved). */
    private int overcrowdedBy(AcademicEventResponseDto event, ClassroomResponseDto classroom) {
        if (classroom == null || classroom.capacity() == null || event == null || event.enrolled() == null) {
            return 0;
        }
        return Math.max(0, event.enrolled() - classroom.capacity());
    }
}
