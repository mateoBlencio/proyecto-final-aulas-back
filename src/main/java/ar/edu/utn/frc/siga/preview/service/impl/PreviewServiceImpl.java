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
import ar.edu.utn.frc.siga.preview.config.PreviewSettings;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class PreviewServiceImpl implements PreviewService {

    private final PreviewEngine previewEngine;
    private final PreviewStore previewStore;
    private final PreviewComposer previewComposer;
    private final PreviewValidator previewValidator;
    private final AllocationValidator validator;
    private final AllocationService allocationService;
    private final AllocationConflictService allocationConflictService;
    private final OccurrenceService occurrenceService;
    private final PreviewSettings previewSettings;

    @Override
    public PreviewResponseDto autoPreview(PreviewRequestDto request) {
        Set<Long> eventIds = resolveEventIds(request);
        int timeLimit = request.timeLimitSeconds() != null
                ? request.timeLimitSeconds() : previewSettings.getDefaultTimeLimitSeconds();
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

    @Override
    @Transactional
    public ConfirmPreviewResponseDto confirm(String previewId, ConfirmPreviewRequestDto request) {
        OptimizationResult preview = previewStore.get(previewId).orElseThrow(() -> new ExpiredPreviewException(previewId));
        Set<Long> previewEventIds = preview.allocations().stream()
                .map(a -> Long.valueOf(a.eventId()))
                .collect(Collectors.toSet());

        previewValidator.validateNoDuplicateEventIds(request.allocations());
        previewValidator.validateAllocationsBelongToPreview(request.allocations(), previewEventIds);

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

        List<AllocationItem> items = eventIdsWithClassroom.stream()
                .map(eventId -> new AllocationItem(new AllocationTarget.Event(eventId), classroomByEvent.get(eventId)))
                .toList();
        List<AllocationResponseDto> saved = allocationService.reallocate(AllocationCommand.automatic(items));
        previewStore.remove(previewId);

        log.info("Confirm aplicado: previewId={}, applied={}, skipped={}",
                previewId, saved.size(), skippedEventIds.size());
        return new ConfirmPreviewResponseDto(saved, skippedEventIds);
    }

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
        return allocationConflictService.resolveAllUnallocatedEventIds().stream()
                .filter(id -> !excludedIds.contains(id))
                .collect(Collectors.toSet());
    }
}
