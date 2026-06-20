package ar.edu.utn.frc.classroom_allocation.allocation.dto.response;

import ar.edu.utn.frc.classroom_allocation.allocation.model.AllocationSource;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Value
@Builder
public class AllocationResponseDto {
    // Asignación
    Long id;
    AllocationSource source;
    LocalDateTime createdAt;
    String observation;
    // Ocurrencia
    Long occurrenceId;
    LocalDate occurrenceDate;
    // Evento
    Long eventId;
    LocalTime startTime;
    LocalTime endTime;
    Integer enrolled;
    String subject;        // RecurringEvent: materia; null para UniqueEvent
    String section;        // RecurringEvent: comisión; null para UniqueEvent
    String eventDescription; // UniqueEvent: descripción; null para RecurringEvent
    // Aula
    Integer classroomId;
    String classroomNumber;
    Integer floor;
    Integer capacity;
    Integer buildingId;
    String buildingName;
    String classroomType;
}
