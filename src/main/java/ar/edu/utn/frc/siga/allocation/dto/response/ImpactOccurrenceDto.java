package ar.edu.utn.frc.siga.allocation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "Clase alcanzada por el pedido, con su aula actual y la pedida")
public record ImpactOccurrenceDto(
        Long occurrenceId,
        Long eventId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        Integer currentClassroomId,
        Integer requestedClassroomId,
        boolean blocked) {
}
