package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.request.AllocateFromDateRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocateOccurrenceRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.BatchReassignRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;

import java.time.LocalDate;
import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface AllocationService {
    AllocationResponseDto findById(Long allocationId);
    AllocationResponseDto assignManually(Long occurrenceId, AllocateOccurrenceRequestDto dto);
    AllocationResponseDto reassign(Long allocationId, AllocateOccurrenceRequestDto dto);
    List<AllocationResponseDto> batchReassign(BatchReassignRequestDto dto);
    List<AllocationResponseDto> assignManuallyFromDate(AllocateFromDateRequestDto dto);
    List<AllocationResponseDto> importAssignmentsFromDate(AllocateFromDateRequestDto dto);
    List<AllocationResponseDto> findByDate(LocalDate date);
}
