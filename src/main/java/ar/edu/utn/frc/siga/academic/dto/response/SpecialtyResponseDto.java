package ar.edu.utn.frc.siga.academic.dto.response;

import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record SpecialtyResponseDto(
        Long id,
        Integer specialtyCode,
        String name,
        String abbreviation,
        boolean enabled,
        UnavailableReason unavailableReason
) {
    public SpecialtyResponseDto(Integer specialtyCode, String name, String abbreviation) {
        this(null, specialtyCode, name, abbreviation, true, null);
    }
}
