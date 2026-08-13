package ar.edu.utn.frc.siga.allocation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record DeallocationBatchRequestDto(
        @NotEmpty @Valid List<DeallocationTargetRequestDto> items,
        String observation
) {}
