package PF.classroom_allocation.solver.service;

import PF.classroom_allocation.solver.dto.request.AllocationRequestDto;
import PF.classroom_allocation.solver.dto.response.AllocationPreviewResponseDto;

public interface AllocationService {

    AllocationPreviewResponseDto preview(AllocationRequestDto request);
}