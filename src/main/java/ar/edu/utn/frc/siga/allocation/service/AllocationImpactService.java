package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationImpactResponseDto;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface AllocationImpactService {

    AllocationImpactResponseDto analyze(AllocationCommand command);
}
