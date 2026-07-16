package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.request.AutoPreviewRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.ConfirmAutoPreviewRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.PreviewAllocationDto;
import ar.edu.utn.frc.siga.allocation.dto.request.ValidateMoveRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AutoPreviewResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ConfirmAutoPreviewResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.MoveConflictDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ProposedAllocationDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ValidateMoveResponseDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
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
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator.AllocationCandidate;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator.OccupiedSlot;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementación de {@link AutoAllocationService}: arma los modelos del solver a partir
 * de los eventos y la ocupación existente ({@link AutoAllocationDataLoader}), delega la
 * optimización en el motor puro ({@code solver::api}) y compone/valida el resultado
 * (preview, validate-move y confirm) contra el estado actual de la base.
 */
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
    private final AllocationValidator validator;

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
        return compose(preview, inputs.events(), inputs.datesByEvent(), inputs.priorRoomByEvent());
    }

    @Override
    public AutoPreviewResponseDto getPreview(String previewId) {
        SolverPreview preview = solverService.getPreview(previewId);
        Set<Long> eventIds = preview.allocations().stream()
                .map(a -> Long.valueOf(a.eventId()))
                .collect(Collectors.toSet());
        AutoPreviewInputs inputs = dataLoader.load(eventIds);
        return compose(preview, inputs.events(), inputs.datesByEvent(), inputs.priorRoomByEvent());
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
        Map<Long, RecurringEvent> eventsById = inputs.events().stream()
                .collect(Collectors.toMap(AcademicEvent::getId, e -> e));

        RecurringEvent movedEvent = eventsById.get(request.eventId());
        Set<LocalDate> movedDates = Set.copyOf(inputs.datesByEvent().getOrDefault(request.eventId(), List.of()));
        LocalTime movedStart = movedEvent.getStartTime();
        LocalTime movedEnd = movedEvent.endTime();

        List<OccupiedSlot> databaseOccupancy = inputs.databaseOccupancy().stream()
                .map(o -> new OccupiedSlot(o.classroomId(), o.date(), o.startTime(), o.endTime(), o.eventId(), o.allocationId()))
                .toList();

        List<MoveConflictDto> conflicts = new ArrayList<>();
        conflicts.addAll(validator.moveDatabaseConflicts(request.classroomId(), movedDates, movedStart, movedEnd, databaseOccupancy));
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

        List<Occurrence> targetOccurrences = occurrenceRepository
                .findByEvent_IdInAndStatusInAndDateGreaterThanEqual(
                        eventIdsWithClassroom, List.of(OccurrenceStatus.SCHEDULED, OccurrenceStatus.ASSIGNED),
                        LocalDate.now())
                .stream()
                .filter(validator::isApplicable)
                .toList();

        List<AllocationCandidate> candidates = targetOccurrences.stream()
                .map(o -> new AllocationCandidate(o, classroomByEvent.get(o.getEvent().getId())))
                .toList();
        List<OccupiedSlot> occupancy = inputs.databaseOccupancy().stream()
                .map(o -> new OccupiedSlot(o.classroomId(), o.date(), o.startTime(), o.endTime(), o.eventId(), o.allocationId()))
                .toList();
        validator.validateNoOverlap(candidates, occupancy);

        List<Allocation> saved = applyAllocations(targetOccurrences, classroomByEvent);
        solverService.invalidatePreview(previewId);

        log.info("Confirm aplicado: previewId={}, applied={}, skipped={}",
                previewId, saved.size(), skippedEventIds.size());
        return new ConfirmAutoPreviewResponseDto(allocationComposer.composeAll(saved), skippedEventIds);
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

    private SolverEvent toSolverEvent(RecurringEvent e, List<LocalDate> dates) {
        String commissionKey = e.getCommissionId() != null ? String.valueOf(e.getCommissionId()) : null;
        return new SolverEvent(String.valueOf(e.getId()), commissionKey, e.getEnrolled(),
                e.getStartTime(), e.endTime(), Set.copyOf(dates));
    }

    /**
     * Compone el DTO propio de allocation a partir de la preview cruda del solver: separa
     * resueltos (con aula) de {@code unresolved} (sin aula, revisión manual), y resuelve
     * evento y aula en un solo batch cada uno. Floor de no-regresión: un evento sin aula del
     * solver que YA estaba asignado ({@code priorRoomByEvent}) conserva esa aula previa y
     * queda en resueltos; sólo los eventos sin aula previa caen en {@code unresolved}.
     */
    private AutoPreviewResponseDto compose(SolverPreview preview, List<RecurringEvent> events,
                                            Map<Long, List<LocalDate>> datesByEvent,
                                            Map<Long, Integer> priorRoomByEvent) {
        Map<Long, RecurringEvent> eventsById = events.stream()
                .collect(Collectors.toMap(AcademicEvent::getId, e -> e));

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

        List<RecurringEvent> referencedEvents = preview.allocations().stream()
                .map(a -> eventsById.get(Long.valueOf(a.eventId())))
                .filter(Objects::nonNull)
                .toList();
        Map<Long, AcademicEventResponseDto> eventDtoById = composeEventsById(referencedEvents);

        Set<Integer> classroomIds = Set.copyOf(effectiveRoomByEventId.values());
        Map<Integer, ClassroomResponseDto> classroomDtoById = classroomService.findByIds(classroomIds).stream()
                .collect(Collectors.toMap(ClassroomResponseDto::id, c -> c));

        List<ProposedAllocationDto> allocations = resolved.stream()
                .map(a -> toProposedAllocationDto(a, eventDtoById, datesByEvent,
                        classroomDtoById.get(effectiveRoomByEventId.get(a.eventId()))))
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
        AcademicEventResponseDto event = eventDtoById.get(eventId);
        return new ProposedAllocationDto(
                event, datesByEvent.getOrDefault(eventId, List.of()), classroom, overcrowdedBy(event, classroom));
    }

    /** Alumnos que exceden la capacidad del aula propuesta (0 si entran, o si la fila es unresolved). */
    private int overcrowdedBy(AcademicEventResponseDto event, ClassroomResponseDto classroom) {
        if (classroom == null || classroom.capacity() == null || event == null || event.enrolled() == null) {
            return 0;
        }
        return Math.max(0, event.enrolled() - classroom.capacity());
    }
}
