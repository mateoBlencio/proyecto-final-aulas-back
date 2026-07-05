package ar.edu.utn.frc.siga.solver.service;

import ar.edu.utn.frc.siga.solver.dto.request.AllocationRequestDto;
import ar.edu.utn.frc.siga.solver.dto.response.AllocationPreviewResponseDto;

public interface SolverService {
    AllocationPreviewResponseDto preview(AllocationRequestDto request);
}
