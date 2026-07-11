package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.request.AutoPreviewRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AutoPreviewResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ProposedAllocationDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.mapper.AcademicEventComposer;
import ar.edu.utn.frc.siga.allocation.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.service.AutoAllocationService;
import ar.edu.utn.frc.siga.allocation.service.impl.AutoAllocationDataLoader.AutoPreviewInputs;
import ar.edu.utn.frc.siga.solver.model.SolverAllocation;
import ar.edu.utn.frc.siga.solver.model.SolverEvent;
import ar.edu.utn.frc.siga.solver.model.SolverPreview;
import ar.edu.utn.frc.siga.solver.service.SolverService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
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

        log.info("Auto-preview: {} events, {} available classrooms, {} occupied slots",
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
