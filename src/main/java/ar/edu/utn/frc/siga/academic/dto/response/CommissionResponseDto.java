package ar.edu.utn.frc.siga.academic.dto.response;

import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record CommissionResponseDto(
        Long id,
        String courseCode,
        AcademicPeriodResponseDto academicPeriod,
        UnavailableReason unavailableReason
) {

    /** Year of study derived from the first digit of courseCode (1-9); null otherwise (e.g. TEST, EXAMEN, 0D0). */
    @JsonProperty("yearLevel")
    public Integer yearLevel() {
        if (courseCode == null || courseCode.isEmpty()) {
            return null;
        }
        char first = courseCode.charAt(0);
        return first >= '1' && first <= '9' ? first - '0' : null;
    }
}
