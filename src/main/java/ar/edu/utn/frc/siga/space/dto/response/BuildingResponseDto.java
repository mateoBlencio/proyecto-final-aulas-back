package ar.edu.utn.frc.siga.space.dto.response;

import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record BuildingResponseDto(
        Long id,
        String name,
        Boolean active,
        UnavailableReason unavailableReason
) {
}
