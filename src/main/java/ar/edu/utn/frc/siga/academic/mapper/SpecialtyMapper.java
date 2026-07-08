package ar.edu.utn.frc.siga.academic.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import org.springframework.stereotype.Component;

@Component
public class SpecialtyMapper {

    public SpecialtyResponseDto toDto(Specialty specialty) {
        if (specialty == null) {
            return null;
        }
        return SpecialtyResponseDto.builder()
                .specialtyCode(specialty.getSpecialtyCode())
                .name(specialty.getName())
                .build();
    }
}