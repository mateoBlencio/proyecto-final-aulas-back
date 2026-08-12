package ar.edu.utn.frc.siga.allocation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Pedido de asignación/reasignación en lote (source siempre MANUAL: nunca lo decide el cliente). */
public record AllocationBatchRequestDto(
        @NotEmpty @Valid List<AllocationItemRequestDto> items,
        String observation
) {}
