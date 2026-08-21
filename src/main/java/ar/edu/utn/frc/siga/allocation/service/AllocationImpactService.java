package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationImpactResponseDto;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;

import org.springframework.modulith.NamedInterface;

/**
 * Simula un pedido de asignación y cuenta qué pasaría, sin tocar la base.
 *
 * <p>Va aparte de {@link AllocationService} a propósito: ese contrato es el de las operaciones que
 * escriben, y meterle un método que no escribe invita a confundirlos.
 */
@NamedInterface("api")
public interface AllocationImpactService {

    AllocationImpactResponseDto analyze(AllocationCommand command);
}
