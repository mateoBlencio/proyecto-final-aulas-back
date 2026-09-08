package ar.edu.utn.frc.siga.academic.dto;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record SpecialtyFilter(
        Integer specialtyCode,
        String name
) {
}
