package ar.edu.utn.frc.classroom_allocation.allocation.service;

import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.AllocateFromDateRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.AllocateOccurrenceRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.response.AllocationResponseDto;

import java.time.LocalDate;
import java.util.List;

public interface AllocationService {
    AllocationResponseDto findById(Long allocationId);
    AllocationResponseDto assign(Long occurrenceId, AllocateOccurrenceRequestDto dto);
    AllocationResponseDto reassign(Long allocationId, AllocateOccurrenceRequestDto dto);
    void cancel(Long allocationId);
    List<AllocationResponseDto> assignFromDate(AllocateFromDateRequestDto dto);
    List<AllocationResponseDto> findByDateAndBuilding(LocalDate date, Integer buildingId);
}
