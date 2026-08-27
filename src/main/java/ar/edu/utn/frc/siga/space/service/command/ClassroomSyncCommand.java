package ar.edu.utn.frc.siga.space.service.command;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record ClassroomSyncCommand(
        Integer roomNumber,
        Integer buildingCode,
        boolean enabled,
        Integer capacity) {
}
