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
        UnavailableReason unavailableReason,
        Integer commissionCount,
        Integer subjectCount
) {
    public SpecialtyResponseDto(Integer specialtyCode, String name, String abbreviation) {
        this(null, specialtyCode, name, abbreviation, true, null, null, null);
    }

    // Static so MapStruct does not read it as a fluent setter for the counts.
    public static SpecialtyResponseDto withCounts(SpecialtyResponseDto dto, int commissionCount, int subjectCount) {
        return new SpecialtyResponseDto(dto.id, dto.specialtyCode, dto.name, dto.abbreviation, dto.enabled,
                dto.unavailableReason, commissionCount, subjectCount);
    }
}
