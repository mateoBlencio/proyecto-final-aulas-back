package ar.edu.utn.frc.siga.academic.dto.response;

import lombok.Builder;
import lombok.Value;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
@Value
@Builder
public class CommissionResponseDto {
    Long id;
    String courseCode;
    Integer commissionNumber;
    Integer yearLevel;
    AcademicPeriodResponseDto academicPeriod;
}