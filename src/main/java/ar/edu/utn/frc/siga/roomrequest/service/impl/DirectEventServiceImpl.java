package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.service.AllocationOccupancyService;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.audit.AuditOperation;
import ar.edu.utn.frc.siga.events.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerAllocation;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerEvent;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerOccupancy;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerRoom;
import ar.edu.utn.frc.siga.optimizer.service.OptimizerService;
import ar.edu.utn.frc.siga.roomrequest.direct.DirectEventDraftValidator;
import ar.edu.utn.frc.siga.roomrequest.direct.DirectEventDraftValidator.ResolvedSchedule;
import ar.edu.utn.frc.siga.roomrequest.direct.DirectEventKinds;
import ar.edu.utn.frc.siga.roomrequest.direct.dto.CreateDirectEventRequestDto;
import ar.edu.utn.frc.siga.roomrequest.direct.dto.DirectEventDraftDto;
import ar.edu.utn.frc.siga.roomrequest.direct.dto.DirectEventSuggestionRequestDto;
import ar.edu.utn.frc.siga.roomrequest.direct.dto.DirectEventSuggestionResponseDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AllowedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestSuggestionResponseDto.SuggestionStatus;
import ar.edu.utn.frc.siga.roomrequest.exception.ExpiredSuggestionException;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.service.DirectEventService;
import ar.edu.utn.frc.siga.roomrequest.service.impl.RoomRequestCandidateResolver.RoomNeeds;
import ar.edu.utn.frc.siga.settings.api.SettingsReader;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomSubjectPermissionDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
public class DirectEventServiceImpl implements DirectEventService {

    private final DirectEventDraftValidator draftValidator;
    private final RoomRequestCandidateResolver candidateResolver;
    private final DirectEventSuggestionStore store;
    private final OptimizerService optimizerService;
    private final SettingsReader settingsReader;
    private final ClassroomService classroomService;
    private final AllocationOccupancyService allocationOccupancyService;
    private final AcademicEventService academicEventService;
    private final OccurrenceService occurrenceService;
    private final AllocationService allocationService;

    @Override
    public List<AllowedClassroomDto> findAvailableClassrooms(DirectEventDraftDto draft, String actor) {
        ResolvedSchedule schedule = draftValidator.validate(draft, actor);
        return candidateResolver.findAllowedClassrooms(needsOf(draft, schedule));
    }

    // Sin @Transactional: el solver no debe correr con una conexión tomada.
    @Override
    public DirectEventSuggestionResponseDto suggest(DirectEventSuggestionRequestDto request, String actor) {
        DirectEventDraftDto draft = request.draft();
        ResolvedSchedule schedule = draftValidator.validate(draft, actor);
        Set<Long> excluded = request.excludedClassroomIds() == null ? Set.of() : Set.copyOf(request.excludedClassroomIds());

        List<ClassroomResponseDto> classrooms = candidateResolver.candidateClassrooms(needsOf(draft, schedule)).stream()
                .filter(c -> !excluded.contains(c.id()))
                .toList();
        if (classrooms.isEmpty()) {
            return noRoomAvailable();
        }

        Map<Long, ClassroomSubjectPermissionDto> permissionsByRoom = classroomService.findSubjectPermissions(
                classrooms.stream().map(ClassroomResponseDto::id).toList());
        List<OptimizerRoom> rooms = classrooms.stream().map(c -> toSolverRoom(c, permissionsByRoom.get(c.id()))).toList();
        List<OptimizerOccupancy> occupancy = allocationOccupancyService.findOccupancy(draft.date(), draft.date()).stream()
                .map(slot -> new OptimizerOccupancy(slot.classroomId(), slot.date(), slot.startTime(), slot.endTime()))
                .toList();

        int count = draft.roomCount();
        int enrolledPerRoom = (draft.estimated() + count - 1) / count;
        int draftHash = draft.hashCode();
        String key = "de-" + draftHash;
        Set<Long> subjectIds = draft.subjectId() == null ? Set.of() : Set.of(draft.subjectId());
        List<OptimizerEvent> events = IntStream.range(0, count)
                .mapToObj(i -> new OptimizerEvent(key + "-" + i, key, enrolledPerRoom, schedule.startTime(),
                        schedule.endTime(), Set.of(draft.date()), subjectIds))
                .toList();
        log.info("Sugerencia de aula para evento directo: {} aulas candidatas, {} franjas ocupadas",
                rooms.size(), occupancy.size());

        OptimizationResult result = optimizerService.optimize(events, rooms, occupancy,
                settingsReader.getInt(SettingKey.PREVIEW_SUGGESTION_TIME_LIMIT_SECONDS));

        List<Long> classroomIds = result.allocations().stream()
                .filter(a -> a.classroomId() != null)
                .sorted(Comparator.comparingInt(a -> Integer.parseInt(a.eventId().substring(a.eventId().lastIndexOf('-') + 1))))
                .map(OptimizerAllocation::classroomId)
                .toList();
        if (classroomIds.isEmpty()) {
            return noRoomAvailable();
        }

        Map<Long, ClassroomResponseDto> byId = classrooms.stream()
                .collect(Collectors.toMap(ClassroomResponseDto::id, c -> c));
        List<ClassroomResponseDto> suggested = classroomIds.stream().map(byId::get).filter(Objects::nonNull).toList();
        int overcrowdedBy = suggested.stream()
                .mapToInt(c -> Math.max(0, enrolledPerRoom - c.capacity()))
                .max()
                .orElse(0);

        String suggestionId = "sug_" + UUID.randomUUID();
        store.save(new DirectEventSuggestion(suggestionId, draftHash, classroomIds));
        SuggestionStatus status = classroomIds.size() == count ? SuggestionStatus.SUGGESTED : SuggestionStatus.PARTIAL;
        log.info("Sugerencia de evento directo generada: suggestionId={}, status={}, aulas={}",
                suggestionId, status, classroomIds);
        List<AllowedClassroomDto> allowed = suggested.stream()
                .map(c -> new AllowedClassroomDto(c.id(), c.roomNumber(), c.buildingId(), c.buildingName(),
                        c.capacity(), true))
                .toList();
        return new DirectEventSuggestionResponseDto(suggestionId, status, allowed, overcrowdedBy);
    }

    @Override
    @Transactional
    @AuditOperation("Alta directa de evento con aula")
    public List<AllocationResponseDto> create(CreateDirectEventRequestDto request, String actor) {
        DirectEventDraftDto draft = request.draft();
        ResolvedSchedule schedule = draftValidator.validate(draft, actor);
        List<Long> classroomIds = request.classroomIds();
        if (classroomIds.size() > draft.roomCount()) {
            throw new InvalidRoomRequestException("No se pueden asignar más aulas que las pedidas (" + draft.roomCount() + ").");
        }
        if (classroomIds.stream().distinct().count() != classroomIds.size()) {
            throw new InvalidRoomRequestException("No se puede repetir un aula en el alta.");
        }

        DirectEventSuggestion suggestion = null;
        if (request.suggestionId() != null) {
            suggestion = store.take(request.suggestionId())
                    .orElseThrow(() -> new ExpiredSuggestionException(request.suggestionId()));
            if (suggestion.draftHash() != draft.hashCode() || !suggestion.classroomIds().equals(classroomIds)) {
                store.save(suggestion);
                throw new ExpiredSuggestionException(request.suggestionId());
            }
        }
        AllocationSource source = suggestion == null ? AllocationSource.MANUAL : AllocationSource.AUTOMATIC;

        try {
            CreateUniqueEventRequestDto dto = new CreateUniqueEventRequestDto(
                    DirectEventKinds.of(draft.type()), draft.subjectId(), draft.commissionId(), draft.date(),
                    schedule.startTime(), (int) schedule.duration().toMinutes(), draft.estimated(),
                    draft.description(), draft.requiresProjector(), draft.requiresComputers(),
                    draft.computerCount(), draft.requiredSoftware(), draft.requiresExamUsers());
            AcademicEventResponseDto event = academicEventService.createUniqueEvent(dto);
            Long principalId = academicEventService.findOccurrencesByEventId(event.id()).getFirst().id();

            List<Long> occurrenceIds = new ArrayList<>(List.of(principalId));
            if (classroomIds.size() > 1) {
                occurrenceIds.addAll(occurrenceService.createSimultaneous(principalId, classroomIds.size() - 1));
            }
            List<AllocationItem> items = IntStream.range(0, classroomIds.size())
                    .mapToObj(i -> new AllocationItem(
                            new AllocationTarget.Occurrences(List.of(occurrenceIds.get(i))), classroomIds.get(i)))
                    .toList();
            List<AllocationResponseDto> response = allocationService.allocate(
                    new AllocationCommand(items, request.reason(), source));
            log.info("Evento directo creado: eventId={}, aulas={}, source={}", event.id(), classroomIds, source);
            return response;
        } catch (RuntimeException e) {
            // El cache no hace rollback con la transacción: sin esto, un 400 o un 409 quemaría la sugerencia.
            if (suggestion != null) {
                store.save(suggestion);
            }
            throw e;
        }
    }

    private static RoomNeeds needsOf(DirectEventDraftDto draft, ResolvedSchedule schedule) {
        return new RoomNeeds(draft.date(), schedule.startTime(), schedule.endTime(),
                Boolean.TRUE.equals(draft.requiresComputers()), draft.computerCount(),
                Boolean.TRUE.equals(draft.requiresProjector()));
    }

    private static OptimizerRoom toSolverRoom(ClassroomResponseDto c, ClassroomSubjectPermissionDto permission) {
        boolean openToAll = permission == null || permission.openToAll();
        Set<Long> allowedSubjectIds = permission == null ? Set.of() : permission.allowedSubjectIds();
        return new OptimizerRoom(c.id(), c.capacity(), c.buildingId(), openToAll, allowedSubjectIds);
    }

    private static DirectEventSuggestionResponseDto noRoomAvailable() {
        return new DirectEventSuggestionResponseDto(null, SuggestionStatus.NO_ROOM_AVAILABLE, List.of(), 0);
    }
}
