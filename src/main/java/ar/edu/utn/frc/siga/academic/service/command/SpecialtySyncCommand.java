package ar.edu.utn.frc.siga.academic.service.command;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record SpecialtySyncCommand(
        Integer specialtyCode,
        String name,
        String abbreviation) {
}
