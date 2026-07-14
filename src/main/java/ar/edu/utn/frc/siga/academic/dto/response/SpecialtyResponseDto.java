package ar.edu.utn.frc.siga.academic.dto.response;

import org.springframework.modulith.NamedInterface;

/** Representación pública de una especialidad. */
@NamedInterface("api")
public record SpecialtyResponseDto(
        Integer specialtyCode,
        String name
) {
}
