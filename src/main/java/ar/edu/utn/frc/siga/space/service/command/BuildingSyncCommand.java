package ar.edu.utn.frc.siga.space.service.command;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record BuildingSyncCommand(
        Integer buildingCode,
        String name) {
}
