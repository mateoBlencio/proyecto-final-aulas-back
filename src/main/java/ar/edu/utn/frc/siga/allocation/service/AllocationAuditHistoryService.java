package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationHistorySnapshotDto;
import ar.edu.utn.frc.siga.common.dto.response.RevisionDto;

import java.util.List;

public interface AllocationAuditHistoryService {

    List<RevisionDto<AllocationHistorySnapshotDto>> findAllocationHistory(Long eventId);
}
