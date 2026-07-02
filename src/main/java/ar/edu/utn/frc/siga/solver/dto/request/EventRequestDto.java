package ar.edu.utn.frc.siga.solver.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Schema(description = "Evento académico a asignar a un aula")
public class EventRequestDto {

    public enum EventType { RECURRING, UNIQUE }

    @NotBlank
    @Schema(description = "Identificador único del evento", requiredMode = Schema.RequiredMode.REQUIRED)
    String id;

    @NotNull
    @Schema(description = "Tipo de evento: RECURRING o UNIQUE", requiredMode = Schema.RequiredMode.REQUIRED)
    EventType type;

    @Schema(description = "Nombre de la materia")
    String subject;

    @Schema(description = "Comisión o sección")
    String section;

    @Min(1)
    @Schema(description = "Cantidad de alumnos inscriptos", example = "30", requiredMode = Schema.RequiredMode.REQUIRED)
    int enrolled;

    @NotNull
    @Schema(description = "Hora de inicio (HH:mm)", example = "08:00", requiredMode = Schema.RequiredMode.REQUIRED)
    LocalTime startTime;

    @Min(1)
    @Schema(description = "Duración en minutos", example = "90", requiredMode = Schema.RequiredMode.REQUIRED)
    int durationMinutes;

    @Schema(description = "[RECURRING] Día de la semana", example = "TUESDAY")
    DayOfWeek dayOfWeek;

    @Schema(description = "[RECURRING] Fecha de inicio de la cursada", example = "2026-03-10")
    LocalDate startDate;

    @Schema(description = "[RECURRING] Fecha de fin de la cursada", example = "2026-07-05")
    LocalDate endDate;

    @Schema(description = "[UNIQUE] Fecha exacta del evento", example = "2026-04-15")
    LocalDate date;

    @AssertTrue(message = "RECURRING events require dayOfWeek, startDate and endDate")
    @Schema(hidden = true)
    public boolean isRecurringFieldsPresent() {
        if (type != EventType.RECURRING) return true;
        return dayOfWeek != null && startDate != null && endDate != null;
    }

    @AssertTrue(message = "UNIQUE events require date")
    @Schema(hidden = true)
    public boolean isUniqueFieldPresent() {
        if (type != EventType.UNIQUE) return true;
        return date != null;
    }

    @AssertTrue(message = "endDate must be >= startDate for RECURRING events")
    @Schema(hidden = true)
    public boolean isDateRangeValid() {
        if (type != EventType.RECURRING) return true;
        if (startDate == null || endDate == null) return true;
        return !endDate.isBefore(startDate);
    }
}
