package ar.edu.utn.frc.siga.academic.dto.response;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record CommissionResponseDto(
        Long id,
        String courseCode,
        Integer commissionNumber,
        Integer yearLevel,
        AcademicPeriodResponseDto academicPeriod
) {
}
