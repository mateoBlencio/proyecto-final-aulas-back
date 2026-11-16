package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.roomrequest.model.RoomPreference;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestAccessControl;
import ar.edu.utn.frc.siga.space.dto.response.AffectedRoomRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.DeactivationImpactDto;
import ar.edu.utn.frc.siga.space.service.ClassroomDeactivationHandler;
import ar.edu.utn.frc.siga.space.service.command.ClassroomDeactivation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
class RoomRequestDeactivationHandler implements ClassroomDeactivationHandler {

    private static final Set<RoomRequestStatus> OPEN_STATUSES = Set.of(RoomRequestStatus.NEW,
            RoomRequestStatus.DERIVED_TO_BUILDING, RoomRequestStatus.IN_EVALUATION, RoomRequestStatus.NOTIFIED);

    private final RoomRequestItemRepository itemRepository;
    private final RoomRequestAccessControl accessControl;
    private final SubjectService subjectService;

    @Override
    public DeactivationImpactDto preview(ClassroomDeactivation deactivation) {
        return affectedRequests(deactivation);
    }

    @Override
    public DeactivationImpactDto apply(ClassroomDeactivation deactivation) {
        return affectedRequests(deactivation);
    }

    private DeactivationImpactDto affectedRequests(ClassroomDeactivation deactivation) {
        Set<Long> classroomIds = deactivation.classroomIds();
        List<RoomRequestItem> items = itemRepository.findOpenRequestingClassrooms(OPEN_STATUSES, classroomIds);
        List<RoomRequestItem> visible = visibleTo(currentActor(), items);
        Map<Long, String> subjectNames = subjectNames(visible);
        List<AffectedRoomRequestDto> details = visible.stream()
                .map(item -> new AffectedRoomRequestDto(item.getId(), item.getRequest().getId(),
                        item.getStatus().name(), item.getRequest().getTeacherName(),
                        subjectNames.get(item.getRequest().getSubjectId()), requestedAmong(item, classroomIds)))
                .toList();
        return new DeactivationImpactDto(0, 0, items.size(), details);
    }

    private List<RoomRequestItem> visibleTo(Optional<String> actor, List<RoomRequestItem> items) {
        if (actor.isEmpty()) {
            return List.of();
        }
        return accessControl.readScope(actor.get())
                .map(scope -> items.stream()
                        .filter(item -> item.getDerivedBuildingId() != null
                                && scope.contains(item.getDerivedBuildingId()))
                        .toList())
                .orElse(items);
    }

    private Map<Long, String> subjectNames(List<RoomRequestItem> items) {
        Set<Long> subjectIds = items.stream().map(item -> item.getRequest().getSubjectId())
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (subjectIds.isEmpty()) {
            return Map.of();
        }
        return subjectService.findByIdsIncludingDeactivated(subjectIds).stream()
                .collect(Collectors.toMap(SubjectResponseDto::id, SubjectResponseDto::name));
    }

    private static List<Long> requestedAmong(RoomRequestItem item, Set<Long> classroomIds) {
        return Stream.concat(
                        item.getPreferences().stream().map(RoomPreference::getClassroomId),
                        item.getAllocations().stream().map(RoomRequestItemAllocation::getClassroomId))
                .filter(classroomIds::contains)
                .distinct()
                .toList();
    }

    private static Optional<String> currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.of(authentication.getName());
    }
}
