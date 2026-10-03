package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerAllocation;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerEvent;
import ar.edu.utn.frc.siga.optimizer.service.OptimizerService;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestSuggestionResponseDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestSuggestionResponseDto.SuggestionStatus;
import ar.edu.utn.frc.siga.roomrequest.exception.ExpiredSuggestionException;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestResolutionService;
import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestSuggestionService;
import ar.edu.utn.frc.siga.settings.api.SettingsReader;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomRequestSuggestionServiceImpl implements RoomRequestSuggestionService {

    private final RoomRequestSuggestionInputLoader inputLoader;
    private final RoomRequestSuggestionStore store;
    private final OptimizerService optimizerService;
    private final SettingsReader settingsReader;
    private final RoomRequestItemRepository itemRepository;
    private final RoomRequestResolutionService resolutionService;

    // Sin @Transactional: el solver no debe correr con una conexión tomada.
    @Override
    public RoomRequestSuggestionResponseDto suggest(Long itemId, Collection<Long> excludedClassroomIds, String actor) {
        Set<Long> excluded = excludedClassroomIds == null ? Set.of() : Set.copyOf(excludedClassroomIds);
        RoomRequestSuggestionInputLoader.Inputs inputs = inputLoader.load(itemId, excluded, actor);
        if (inputs.rooms().isEmpty()) {
            return noRoomAvailable(itemId);
        }

        List<OptimizerEvent> events = IntStream.range(0, inputs.classroomCount())
                .mapToObj(i -> new OptimizerEvent("rr-" + itemId + "-" + i, "rr-" + itemId, inputs.enrolled(),
                        inputs.startTime(), inputs.endTime(), inputs.dates(), inputs.subjectIds()))
                .toList();
        log.info("Sugerencia de aula para pedido: itemId={}, {} aulas candidatas, {} franjas ocupadas",
                itemId, inputs.rooms().size(), inputs.occupancy().size());

        OptimizationResult result = optimizerService.optimize(events, inputs.rooms(), inputs.occupancy(),
                settingsReader.getInt(SettingKey.PREVIEW_SUGGESTION_TIME_LIMIT_SECONDS));

        List<Long> classroomIds = result.allocations().stream()
                .filter(a -> a.classroomId() != null)
                .sorted(Comparator.comparing(OptimizerAllocation::eventId))
                .map(OptimizerAllocation::classroomId)
                .toList();
        if (classroomIds.isEmpty()) {
            return noRoomAvailable(itemId);
        }

        Map<Long, ClassroomResponseDto> byId = inputs.classrooms().stream()
                .collect(Collectors.toMap(ClassroomResponseDto::id, c -> c));
        List<ClassroomResponseDto> suggested = classroomIds.stream().map(byId::get).filter(Objects::nonNull).toList();
        int overcrowdedBy = suggested.stream()
                .mapToInt(c -> Math.max(0, inputs.enrolled() - c.capacity()))
                .max()
                .orElse(0);

        String suggestionId = "sug_" + UUID.randomUUID();
        store.save(new RoomRequestSuggestion(suggestionId, itemId, inputs.itemVersion(), classroomIds));
        SuggestionStatus status = classroomIds.size() == inputs.classroomCount()
                ? SuggestionStatus.SUGGESTED : SuggestionStatus.PARTIAL;
        log.info("Sugerencia generada: suggestionId={}, itemId={}, status={}, aulas={}",
                suggestionId, itemId, status, classroomIds);
        return new RoomRequestSuggestionResponseDto(suggestionId, itemId, status, suggested, overcrowdedBy);
    }

    @Override
    @Transactional
    public RoomRequestItemResponseDto confirm(Long itemId, String suggestionId, String reason, String actor) {
        RoomRequestSuggestion suggestion = store.take(suggestionId)
                .filter(s -> s.itemId().equals(itemId))
                .orElseThrow(() -> new ExpiredSuggestionException(suggestionId));
        Long currentVersion = itemRepository.findById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId))
                .getVersion();
        if (!currentVersion.equals(suggestion.itemVersion())) {
            throw new ExpiredSuggestionException(suggestionId);
        }
        RoomRequestItemResponseDto response =
                resolutionService.assignAutomatic(itemId, suggestion.classroomIds(), reason, actor);
        log.info("Confirm de sugerencia de pedido: suggestionId={}, itemId={}", suggestionId, itemId);
        return response;
    }

    private RoomRequestSuggestionResponseDto noRoomAvailable(Long itemId) {
        log.info("Sin aula sugerida para pedido: itemId={}", itemId);
        return new RoomRequestSuggestionResponseDto(null, itemId, SuggestionStatus.NO_ROOM_AVAILABLE, List.of(), 0);
    }
}
