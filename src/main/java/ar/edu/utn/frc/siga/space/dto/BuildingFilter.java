package ar.edu.utn.frc.siga.space.dto;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record BuildingFilter(
        String name
) {
}
