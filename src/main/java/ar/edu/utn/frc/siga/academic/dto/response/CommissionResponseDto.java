package ar.edu.utn.frc.siga.academic.dto.response;

import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import java.util.List;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record CommissionResponseDto(
        Long id,
        String courseCode,
        AcademicPeriodResponseDto academicPeriod,
        UnavailableReason unavailableReason,
        List<SpecialtySummaryDto> specialties
) {

    public CommissionResponseDto(Long id, String courseCode, AcademicPeriodResponseDto academicPeriod,
            UnavailableReason unavailableReason) {
        this(id, courseCode, academicPeriod, unavailableReason, null);
    }
}
