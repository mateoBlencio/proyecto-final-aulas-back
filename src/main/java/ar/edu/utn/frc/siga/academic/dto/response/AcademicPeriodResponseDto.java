package ar.edu.utn.frc.siga.academic.dto.response;

import lombok.Builder;
import lombok.Value;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
@Value
@Builder
public class AcademicPeriodResponseDto {
    Integer year;
    Integer semester;
}