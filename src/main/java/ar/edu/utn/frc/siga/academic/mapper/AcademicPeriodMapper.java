package ar.edu.utn.frc.siga.academic.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import org.springframework.stereotype.Component;

@Component
public class AcademicPeriodMapper {

    public AcademicPeriodResponseDto toDto(AcademicPeriod period) {
        if (period == null) {
            return null;
        }
        return AcademicPeriodResponseDto.builder()
                .year(period.getYear())
                .semester(period.getSemester())
                .build();
    }
}