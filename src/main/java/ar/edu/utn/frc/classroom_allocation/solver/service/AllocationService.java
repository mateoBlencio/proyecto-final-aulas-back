package ar.edu.utn.frc.classroom_allocation.solver.service;

import ar.edu.utn.frc.classroom_allocation.solver.dto.request.AllocationRequestDto;
import ar.edu.utn.frc.classroom_allocation.solver.dto.response.AllocationPreviewResponseDto;

public interface AllocationService {

    AllocationPreviewResponseDto preview(AllocationRequestDto request);
}