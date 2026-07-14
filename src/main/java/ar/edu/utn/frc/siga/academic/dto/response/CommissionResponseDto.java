package ar.edu.utn.frc.siga.academic.dto.response;

import org.springframework.modulith.NamedInterface;

/** Representación pública de una comisión, con su período académico. */
@NamedInterface("api")
public record CommissionResponseDto(
        Long id,
        String courseCode,
        Integer commissionNumber,
        Integer yearLevel,
        AcademicPeriodResponseDto academicPeriod
) {
}
