package ar.edu.utn.frc.siga.sysacad.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KeepSysacadRequestDto(
        @NotBlank @Size(max = 255) String observation
) {}
