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
    AllocationResponseDto assign(Long occurrenceId, AllocateOccurrenceRequestDto dto);
    AllocationResponseDto reassign(Long allocationId, AllocateOccurrenceRequestDto dto);
    List<AllocationResponseDto> batchReassign(BatchReassignRequestDto dto);
    void cancel(Long allocationId);
    List<AllocationResponseDto> assignFromDate(AllocateFromDateRequestDto dto);
    List<AllocationResponseDto> assignAllFromDate(AllocateFromDateRequestDto dto);
    List<AllocationResponseDto> findByDate(LocalDate date);
}
