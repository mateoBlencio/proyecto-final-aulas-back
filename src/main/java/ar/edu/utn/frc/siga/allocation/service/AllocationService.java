package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.request.AllocateFromDateRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocateOccurrenceRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.BatchReassignRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationSummaryDto;

import java.time.LocalDate;
import java.util.List;

public interface AllocationService {
    AllocationResponseDto findById(Long allocationId);
    AllocationResponseDto assign(Long occurrenceId, AllocateOccurrenceRequestDto dto);
    AllocationResponseDto reassign(Long allocationId, AllocateOccurrenceRequestDto dto);
    List<AllocationResponseDto> batchReassign(BatchReassignRequestDto dto);
    void cancel(Long allocationId);
    List<AllocationResponseDto> assignFromDate(AllocateFromDateRequestDto dto);
    List<AllocationSummaryDto> findByDate(LocalDate date);
}
