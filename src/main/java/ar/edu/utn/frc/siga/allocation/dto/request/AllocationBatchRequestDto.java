package ar.edu.utn.frc.siga.allocation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AllocationBatchRequestDto(
        @NotEmpty @Valid List<AllocationItemRequestDto> items,
        @Size(max = 255) String observation
) {}
