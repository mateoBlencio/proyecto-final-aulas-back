package ar.edu.utn.frc.siga.space.dto.response;

import ar.edu.utn.frc.siga.space.model.ResourceValueKind;

public record ResourceTypeResponseDto(
        Long id,
        String name,
        ResourceValueKind valueKind,
        boolean enabled
) {
}
