package ar.edu.utn.frc.siga.dashboard.dto.response;

import ar.edu.utn.frc.siga.dashboard.model.ExcludedDayReason;
import java.time.LocalDate;

public record ExcludedDayResponseDto(LocalDate date, ExcludedDayReason reason, String description) {
}
