package ar.edu.utn.frc.siga.preview.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.service.AllocationConflictService;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.validator.AllocationCandidate;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator;
import ar.edu.utn.frc.siga.common.exception.InvalidSelectionException;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.preview.dto.request.ConfirmPreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewAllocationDto;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.request.ReallocationSuggestionRequestDto;
import ar.edu.utn.frc.siga.preview.dto.response.ConfirmPreviewResponseDto;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewResponseDto;
import ar.edu.utn.frc.siga.preview.dto.response.ReallocationSuggestionResponseDto;
import ar.edu.utn.frc.siga.preview.dto.response.ReallocationSuggestionResponseDto.SuggestionStatus;
import ar.edu.utn.frc.siga.preview.config.PreviewSettings;
import ar.edu.utn.frc.siga.preview.mapper.PreviewComposer;
import ar.edu.utn.frc.siga.preview.service.PreviewService;
import ar.edu.utn.frc.siga.preview.service.PreviewStore;
import ar.edu.utn.frc.siga.preview.service.ReallocationSuggestion;
import ar.edu.utn.frc.siga.preview.validator.PreviewValidator;
import ar.edu.utn.frc.siga.preview.exception.ExpiredPreviewException;
import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
        PreviewEngine.Inputs inputs = previewEngine.loadInputs(eventIdsOf(previewKeys(preview)));
        return previewComposer.compose(preview, inputs.events(), inputs.datesByKey(), inputs.priorRoomByKey(),
                inputs.priorSlotsByKey(), inputs.rooms(), inputs.databaseOccupancy());
    }

    private static Set<PlanningKey> previewKeys(OptimizationResult preview) {
        return preview.allocations().stream()
                .map(a -> PlanningKey.parse(a.eventId()))
                .collect(Collectors.toSet());
    }

    private static Set<Long> eventIdsOf(Collection<PlanningKey> keys) {
        return keys.stream().map(PlanningKey::eventId).collect(Collectors.toSet());
    }

    // Un evento con un roomSlot con aula y otro sin ella figura en skippedEventIds (por el roomSlot
    // sin aula) y también en lo aplicado (por el otro).
    @Override
    @Transactional
    public ConfirmPreviewResponseDto confirm(String previewId, ConfirmPreviewRequestDto request) {
        OptimizationResult preview = previewStore.get(previewId).orElseThrow(() -> new ExpiredPreviewException(previewId));

        previewValidator.validateNoDuplicateAllocations(request.allocations());
        previewValidator.validateAllocationsBelongToPreview(request.allocations(), previewKeys(preview));

        Map<PlanningKey, Long> classroomByKey = new LinkedHashMap<>();
        for (PreviewAllocationDto allocation : request.allocations()) {
            classroomByKey.put(new PlanningKey(allocation.eventId(), allocation.effectiveRoomSlot()),
                    allocation.classroomId());
        }
        Set<Long> skippedIds = classroomByKey.entrySet().stream()
                .filter(entry -> entry.getValue() == null)
                .map(entry -> entry.getKey().eventId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<PlanningKey, Long> appliedByKey = classroomByKey.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (x, y) -> x, LinkedHashMap::new));

        if (appliedByKey.isEmpty()) {
            log.info("Confirm sin aulas propuestas: previewId={}, skipped={}", previewId, skippedIds.size());
            return new ConfirmPreviewResponseDto(List.of(), List.copyOf(skippedIds));
        }

        Set<Long> eventIdsWithClassroom = eventIdsOf(appliedByKey.keySet());
        PreviewEngine.Inputs inputs = previewEngine.loadInputs(eventIdsWithClassroom);
        validator.validateClassroomsAvailable(new HashSet<>(appliedByKey.values()));

        Map<PlanningKey, List<OccurrenceSlotDto>> occurrencesByKey = occurrenceService
                .findSlotsByEvents(eventIdsWithClassroom, LocalDate.now())
                .stream()
                .filter(o -> o.status() == OccurrenceStatus.NEEDS_ROOM && !o.isPast())
                .collect(Collectors.groupingBy(o -> new PlanningKey(o.eventId(), o.roomSlot())));

        List<AllocationCandidate> candidates = new ArrayList<>();
        List<AllocationItem> items = new ArrayList<>();
        appliedByKey.forEach((key, classroomId) -> {
            List<OccurrenceSlotDto> occurrences = occurrencesByKey.getOrDefault(key, List.of());
            occurrences.forEach(o -> candidates.add(new AllocationCandidate(o, classroomId)));
            if (occurrences.isEmpty()) {
                skippedIds.add(key.eventId());
            } else {
                items.add(new AllocationItem(new AllocationTarget.Occurrences(
                        occurrences.stream().map(OccurrenceSlotDto::occurrenceId).toList()), classroomId));
            }
        });
        List<Long> skippedEventIds = List.copyOf(skippedIds);
        if (items.isEmpty()) {
            log.info("Confirm sin ocurrencias pendientes: previewId={}, skipped={}", previewId, skippedEventIds.size());
            return new ConfirmPreviewResponseDto(List.of(), skippedEventIds);
        }
        previewValidator.validateNoSiblingRoomClash(candidates);
        validator.validateNoOverlap(candidates, inputs.databaseOccupancy());

        List<AllocationResponseDto> saved = allocationService.reallocate(AllocationCommand.automatic(items));
        previewStore.remove(previewId);

        log.info("Confirm aplicado: previewId={}, applied={}, skipped={}",
                previewId, saved.size(), skippedEventIds.size());
        return new ConfirmPreviewResponseDto(saved, skippedEventIds);
    }

    @Override
    public ReallocationSuggestionResponseDto suggestReallocation(ReallocationSuggestionRequestDto request) {
        AllocationTarget target = request.eventId() != null
                ? new AllocationTarget.EventRange(request.eventId(), request.from(), request.to())
                : new AllocationTarget.Occurrences(request.occurrenceIds());
        List<OccurrenceSlotDto> slots = allocationService.resolveOccurrences(target).stream()
                .filter(o -> o.status() != OccurrenceStatus.ROOM_RELEASED)
                .toList();
        if (slots.isEmpty()) {
            throw new AllocationConflictException("el target no tiene ocurrencias pendientes de asignación");
        }
        Set<Long> eventIds = slots.stream().map(OccurrenceSlotDto::eventId).collect(Collectors.toSet());
        if (eventIds.size() > 1) {
            throw new InvalidSelectionException("Las ocurrencias indicadas pertenecen a más de un evento");
        }
        Long eventId = eventIds.iterator().next();
        List<OccurrenceSlotDto> orderedSlots = slots.stream()
                .sorted(Comparator.comparing(OccurrenceSlotDto::date))
                .toList();
        List<Long> occurrenceIds = orderedSlots.stream().map(OccurrenceSlotDto::occurrenceId).toList();
        List<LocalDate> dates = orderedSlots.stream().map(OccurrenceSlotDto::date).toList();
        Set<Long> excludedClassroomIds = request.excludedClassroomIds() != null
                ? Set.copyOf(request.excludedClassroomIds()) : Set.of();

        PreviewEngine.Suggestion suggestion = previewEngine.suggest(
                eventId, orderedSlots, excludedClassroomIds, previewSettings.getSuggestionTimeLimitSeconds());

        if (suggestion.classroom() == null) {
            log.info("Sin aula sugerida: eventId={}, ocurrencias={}", eventId, occurrenceIds.size());
            return new ReallocationSuggestionResponseDto(
                    null, eventId, occurrenceIds, dates, SuggestionStatus.NO_ROOM_AVAILABLE, null, 0);
        }

        String suggestionId = "sug_" + UUID.randomUUID();
        previewStore.saveSuggestion(
                new ReallocationSuggestion(suggestionId, eventId, occurrenceIds, suggestion.classroom().id()));
        log.info("Sugerencia generada: suggestionId={}, eventId={}, classroomId={}",
                suggestionId, eventId, suggestion.classroom().id());
        return new ReallocationSuggestionResponseDto(suggestionId, eventId, occurrenceIds, dates,
                SuggestionStatus.SUGGESTED, suggestion.classroom(), suggestion.overcrowdedBy());
    }

    @Override
    @Transactional
    public List<AllocationResponseDto> confirmReallocationSuggestion(String suggestionId) {
        ReallocationSuggestion suggestion = previewStore.takeSuggestion(suggestionId)
                .orElseThrow(() -> new ExpiredPreviewException(suggestionId));
        AllocationItem item = new AllocationItem(
                new AllocationTarget.Occurrences(suggestion.occurrenceIds()), suggestion.classroomId());
        List<AllocationResponseDto> saved = allocationService.reallocate(AllocationCommand.automatic(List.of(item)));
        log.info("Confirm de sugerencia: suggestionId={}, applied={}", suggestionId, saved.size());
        return saved;
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
