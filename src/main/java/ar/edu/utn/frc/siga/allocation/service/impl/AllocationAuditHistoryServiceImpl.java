package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationHistorySnapshotDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.service.AllocationAuditHistoryService;
import ar.edu.utn.frc.siga.audit.service.RevisionReader;
import ar.edu.utn.frc.siga.audit.dto.response.RevisionDto;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AllocationAuditHistoryServiceImpl implements AllocationAuditHistoryService {

    private final RevisionReader revisionReader;
    private final OccurrenceService occurrenceService;
    private final AcademicEventService academicEventService;
    private final ClassroomService classroomService;
    private final BuildingScopeResolver buildingScopeResolver;

    @Override
    @Transactional(readOnly = true)
    public List<RevisionDto<AllocationHistorySnapshotDto>> findAllocationHistory(Long eventId) {
        academicEventService.findById(eventId);

        List<Long> occurrenceIds = occurrenceService.findSlotsByEvent(eventId, null).stream()
                .map(OccurrenceSlotDto::occurrenceId)
                .toList();

        List<RevisionDto<AllocationHistorySnapshotDto>> revisions =
                revisionReader.read(Allocation.class, "occurrenceId", occurrenceIds, this::toSnapshot);
        BuildingScope scope = buildingScopeResolver.scopeFor(Permission.ALLOCATION_HISTORY_READ);
        return scope.isUnrestricted() ? revisions : withinScope(revisions, scope);
    }

    private List<RevisionDto<AllocationHistorySnapshotDto>> withinScope(
            List<RevisionDto<AllocationHistorySnapshotDto>> revisions, BuildingScope scope) {
        Set<Long> classroomIds = revisions.stream()
                .map(RevisionDto::snapshot)
                .filter(Objects::nonNull)
                .map(AllocationHistorySnapshotDto::classroomId)
                .collect(Collectors.toSet());
        Set<Long> classroomsInScope = classroomService.findByIdsIncludingDeactivated(classroomIds).stream()
                .filter(classroom -> scope.allows(classroom.buildingId()))
                .map(ClassroomResponseDto::id)
                .collect(Collectors.toSet());

        Map<Object, Long> lastClassroomByAllocation = new HashMap<>();
        List<RevisionDto<AllocationHistorySnapshotDto>> visible = new ArrayList<>();
        for (RevisionDto<AllocationHistorySnapshotDto> revision : revisions) {
            AllocationHistorySnapshotDto snapshot = revision.snapshot();
            boolean inScope;
            if (snapshot == null) {
                inScope = classroomsInScope.contains(lastClassroomByAllocation.get(revision.entityId()));
            } else {
                Long previousClassroom = lastClassroomByAllocation.put(revision.entityId(), snapshot.classroomId());
                inScope = classroomsInScope.contains(snapshot.classroomId())
                        || (previousClassroom != null && classroomsInScope.contains(previousClassroom));
            }
            if (inScope) {
                visible.add(revision);
            }
        }
        return visible;
    }

    private AllocationHistorySnapshotDto toSnapshot(Allocation allocation) {
        return new AllocationHistorySnapshotDto(
                allocation.getId(),
                allocation.getOccurrenceId(),
                allocation.getClassroomId(),
                allocation.getSource(),
                allocation.getCreatedAt(),
                allocation.getObservation());
    }
}
