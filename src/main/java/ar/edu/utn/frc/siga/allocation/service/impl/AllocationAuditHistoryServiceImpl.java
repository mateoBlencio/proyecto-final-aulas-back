package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationHistorySnapshotDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.service.AllocationAuditHistoryService;
import ar.edu.utn.frc.siga.audit.RevisionReader;
import ar.edu.utn.frc.siga.audit.RevisionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AllocationAuditHistoryServiceImpl implements AllocationAuditHistoryService {

    private final RevisionReader revisionReader;
    private final OccurrenceService occurrenceService;
    private final AcademicEventService academicEventService;

    @Override
    @Transactional(readOnly = true)
    public List<RevisionDto<AllocationHistorySnapshotDto>> findAllocationHistory(Long eventId) {
        academicEventService.findById(eventId);

        List<Long> occurrenceIds = occurrenceService.findSlotsByEvent(eventId, null).stream()
                .map(OccurrenceSlotDto::occurrenceId)
                .toList();

        return revisionReader.read(Allocation.class, "occurrenceId", occurrenceIds, this::toSnapshot);
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
