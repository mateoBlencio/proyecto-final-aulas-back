package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Schema(description = "Ocurrencia del pedido que no se puede aplicar, y cómo destrabarla")
public record ImpactConflictDto(
        Long occurrenceId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        Long requestedClassroomId,
        ImpactBlockerDto blockedBy,
        List<ClassroomResponseDto> alternativeClassrooms) {
}
