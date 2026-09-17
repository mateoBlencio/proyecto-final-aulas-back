package ar.edu.utn.frc.siga.academic.validator;

import ar.edu.utn.frc.siga.academic.dto.request.UpdateAcademicPeriodRequestDto;
import ar.edu.utn.frc.siga.academic.exception.InvalidAcademicPeriodUpdateException;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.TermType;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class AcademicPeriodUpdateValidator {

    public void validate(AcademicPeriod period, UpdateAcademicPeriodRequestDto dto) {
        LocalDate effectiveStart = dto.startDate() != null ? dto.startDate() : period.getStartDate();

        if (dto.startDate() != null && !Objects.equals(dto.startDate(), period.getStartDate())
                && period.getStartDate() != null && !period.getStartDate().isAfter(LocalDate.now())) {
            throw new InvalidAcademicPeriodUpdateException(
                    "No se puede cambiar la fecha de inicio: el " + period.getStartDate() + " ya ocurrió.");
        }
        if (effectiveStart != null && !dto.endDate().isAfter(effectiveStart)) {
            throw new InvalidAcademicPeriodUpdateException(
                    "La fecha de fin debe ser posterior a la de inicio.");
        }

        boolean recessRequested = dto.recessStart() != null || dto.recessEnd() != null;
        if (!recessRequested) {
            return;
        }
        if (period.getSemester() != TermType.ANUAL.getSemester()) {
            throw new InvalidAcademicPeriodUpdateException(
                    "El receso invernal sólo se define en el período ANUAL.");
        }
        if (dto.recessStart() == null || dto.recessEnd() == null) {
            throw new InvalidAcademicPeriodUpdateException(
                    "El receso necesita fecha de inicio y de fin.");
        }
        if (!dto.recessEnd().isAfter(dto.recessStart())) {
            throw new InvalidAcademicPeriodUpdateException(
                    "El fin del receso debe ser posterior a su inicio.");
        }
        if (effectiveStart != null && dto.recessStart().isBefore(effectiveStart)) {
            throw new InvalidAcademicPeriodUpdateException(
                    "El receso no puede empezar antes del período.");
        }
        if (dto.recessEnd().isAfter(dto.endDate())) {
            throw new InvalidAcademicPeriodUpdateException(
                    "El receso no puede terminar después del período.");
        }
    }
}
