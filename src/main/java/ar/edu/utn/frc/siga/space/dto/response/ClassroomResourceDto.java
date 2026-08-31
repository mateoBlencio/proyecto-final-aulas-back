package ar.edu.utn.frc.siga.space.dto.response;

import ar.edu.utn.frc.siga.space.model.ResourceValueKind;

public record ClassroomResourceDto(
        String code,
        String name,
        ResourceValueKind valueKind,
        Integer quantity
) {
}
