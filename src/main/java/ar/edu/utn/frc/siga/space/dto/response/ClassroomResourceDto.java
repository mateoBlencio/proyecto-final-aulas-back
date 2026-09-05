package ar.edu.utn.frc.siga.space.dto.response;

import ar.edu.utn.frc.siga.space.model.ResourceValueKind;

public record ClassroomResourceDto(
        Long resourceTypeId,
        String name,
        ResourceValueKind valueKind,
        Integer quantity
) {
}
