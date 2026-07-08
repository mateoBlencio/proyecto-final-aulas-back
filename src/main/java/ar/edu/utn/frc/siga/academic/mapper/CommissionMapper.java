package ar.edu.utn.frc.siga.academic.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.model.Commission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommissionMapper {

    private final AcademicPeriodMapper academicPeriodMapper;

    public CommissionResponseDto toDto(Commission commission) {
        return CommissionResponseDto.builder()
                .id(commission.getId())
                .courseCode(commission.getCourseCode())
                .commissionNumber(commission.getCommissionNumber())
                .yearLevel(commission.getYearLevel())
                .academicPeriod(academicPeriodMapper.toDto(commission.getAcademicPeriod()))
                .build();
    }
}