package ar.edu.utn.frc.siga.preview.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.service.AllocationConflictService;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.validator.AllocationCandidate;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator;
import ar.edu.utn.frc.siga.common.exception.InvalidSelectionException;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.preview.dto.request.ConfirmPreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewAllocationDto;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.response.ConfirmPreviewResponseDto;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewResponseDto;
import ar.edu.utn.frc.siga.preview.mapper.PreviewComposer;
import ar.edu.utn.frc.siga.preview.service.PreviewService;
import ar.edu.utn.frc.siga.preview.service.PreviewStore;
import ar.edu.utn.frc.siga.preview.validator.PreviewValidator;
import ar.edu.utn.frc.siga.preview.exception.ExpiredPreviewException;
import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Arma los modelos del solver, delega la optimización y compone/valida el resultado (preview y confirm). */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreviewServiceImpl implements PreviewService {

    private static final int DEFAULT_TIME_LIMIT_SECONDS = 30;

    private final PreviewEngine previewEngine;
    private final PreviewStore previewStore;
    private final PreviewComposer previewComposer;
    private final PreviewValidator previewValidator;
    private final AllocationValidator validator;
    private final AllocationService allocationService;
    private final AllocationConflictService allocationConflictService;
    private final OccurrenceService occurrenceService;

    @Override
    public PreviewResponseDto autoPreview(PreviewRequestDto request) {
        Set<Long> eventIds = resolveEventIds(request);
        int timeLimit = request.timeLimitSeconds() != null
                ? request.timeLimitSeconds() : DEFAULT_TIME_LIMIT_SECONDS;
        log.info("Auto-preview: {} eventos, límite {}s", eventIds.size(), timeLimit);

        OptimizationResult preview = previewEngine.generate(eventIds, timeLimit);
        previewStore.save(preview);
        return composeForResponse(preview);
    }

    @Override
    public PreviewResponseDto getPreview(String previewId) {
        OptimizationResult preview = previewStore.get(previewId).orElseThrow(() -> new ExpiredPreviewException(previewId));
        return composeForResponse(preview);
    }

    private PreviewResponseDto composeForResponse(OptimizationResult preview) {
        Set<Long> eventIds = preview.allocations().stream()
                .map(a -> Long.valueOf(a.eventId()))
                .collect(Collectors.toSet());
        PreviewEngine.Inputs inputs = previewEngine.loadInputs(eventIds);
        return previewComposer.compose(preview, inputs.events(), inputs.datesByEvent(), inputs.priorRoomByEvent(),
                inputs.rooms(), inputs.databaseOccupancy());
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
    public ConfirmPreviewResponseDto confirm(String previewId, ConfirmPreviewRequestDto request) {
        OptimizationResult preview = previewStore.get(previewId).orElseThrow(() -> new ExpiredPreviewException(previewId));
        Set<Long> previewEventIds = preview.allocations().stream()
                .map(a -> Long.valueOf(a.eventId()))
                .collect(Collectors.toSet());

        previewValidator.validateNoDuplicateEventIds(request.allocations());
        previewValidator.validateAllocationsBelongToPreview(request.allocations(), previewEventIds);

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
            return new ConfirmPreviewResponseDto(List.of(), skippedEventIds);
        }

        PreviewEngine.Inputs inputs = previewEngine.loadInputs(eventIdsWithClassroom);
        Set<Integer> classroomIds = eventIdsWithClassroom.stream().map(classroomByEvent::get).collect(Collectors.toSet());
        validator.validateClassroomsAvailable(classroomIds);

        List<OccurrenceSlotDto> targetOccurrences = occurrenceService
                .findSlotsByEvents(eventIdsWithClassroom, LocalDate.now())
                .stream()
                .filter(o -> !o.isPast())
                .toList();

        List<AllocationCandidate> candidates = targetOccurrences.stream()
                .map(o -> new AllocationCandidate(o, classroomByEvent.get(o.eventId())))
                .toList();
        validator.validateNoOverlap(candidates, inputs.databaseOccupancy());

        // Un item por evento (no por occurrence): allocationService.reallocate resuelve él
        // mismo, por AllocationTarget.Event, las occurrences aplicables de cada uno en una
        // sola pasada de escritura — evita N+1 con muchos eventos.
        List<AllocationItem> items = eventIdsWithClassroom.stream()
                .map(eventId -> new AllocationItem(new AllocationTarget.Event(eventId), classroomByEvent.get(eventId)))
                .toList();
        List<AllocationResponseDto> saved = allocationService.reallocate(AllocationCommand.automatic(items));
        previewStore.remove(previewId);

        log.info("Confirm aplicado: previewId={}, applied={}, skipped={}",
                previewId, saved.size(), skippedEventIds.size());
        return new ConfirmPreviewResponseDto(saved, skippedEventIds);
    }

    /**
     * Dos modos excluyentes: {@code eventIds} explícito, o {@code selectAll=true} para
     * resolver todos los eventos sin aula ({@link AllocationConflictService#resolveAllUnassignedEventIds})
     * descontando {@code excludedIds}. Ninguno o ambos a la vez es un request inválido.
     */
    private Set<Long> resolveEventIds(PreviewRequestDto request) {
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
        return allocationConflictService.resolveAllUnassignedEventIds().stream()
                .filter(id -> !excludedIds.contains(id))
                .collect(Collectors.toSet());
    }
}
