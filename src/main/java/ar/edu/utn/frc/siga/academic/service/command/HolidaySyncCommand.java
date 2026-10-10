package ar.edu.utn.frc.siga.academic.service.command;

import java.time.LocalDate;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record HolidaySyncCommand(LocalDate date, String description) {
}
