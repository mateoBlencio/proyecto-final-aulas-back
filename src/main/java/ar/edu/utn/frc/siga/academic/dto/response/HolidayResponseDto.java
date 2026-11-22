package ar.edu.utn.frc.siga.academic.dto.response;

import java.time.LocalDate;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record HolidayResponseDto(LocalDate date, String description) {
}
