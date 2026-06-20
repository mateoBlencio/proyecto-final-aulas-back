package ar.edu.utn.frc.classroom_allocation.allocation.service;

import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.AssignFromDateRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.AssignOccurrenceRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.response.AllocationResponseDto;

import java.util.List;

public interface AllocationService {
    AllocationResponseDto assign(Long occurrenceId, AssignOccurrenceRequestDto dto);
    AllocationResponseDto reassign(Long allocationId, AssignOccurrenceRequestDto dto);
    void cancel(Long allocationId);
    List<AllocationResponseDto> assignFromDate(AssignFromDateRequestDto dto);
}
