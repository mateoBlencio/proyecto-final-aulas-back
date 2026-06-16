package ar.edu.utn.frc.classroom_allocation.solver.dto.response;

import ar.edu.utn.frc.classroom_allocation.solver.dto.request.EventRequestDto.EventType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Value
@Builder
@Schema(description = "Resumen del evento académico asignado")
public class EventSummaryDto {

    @Schema(description = "Identificador del evento")
    String id;

    @Schema(description = "Tipo de evento: RECURRING o UNIQUE")
    EventType type;

    @Schema(description = "Nombre de la materia")
    String subject;

    @Schema(description = "Comisión o sección")
    String section;

    @Schema(description = "Alumnos inscriptos")
    int enrolled;

    @Schema(description = "Hora de inicio")
    LocalTime startTime;

    @Schema(description = "Duración en minutos")
    int durationMinutes;

    @Schema(description = "[RECURRING] Día de la semana")
    DayOfWeek dayOfWeek;

    @Schema(description = "[RECURRING] Fecha de inicio")
    LocalDate startDate;

    @Schema(description = "[RECURRING] Fecha de fin")
    LocalDate endDate;

    @Schema(description = "[UNIQUE] Fecha exacta")
    LocalDate date;
}
