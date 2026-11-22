package ar.edu.utn.frc.siga.allocation.model;

import org.springframework.modulith.NamedInterface;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Cambios de aula hechos por una persona en una misma operación; {@code from}/{@code to} nulos son alta/liberación. */
@NamedInterface("api")
public record ClassroomMovements(String operationId, String actor, AllocationSource source, String reason,
                                 Instant occurredAt, List<Change> changes) {

    public record Change(Long occurrenceId, Long eventId, LocalDate date, LocalTime startTime, LocalTime endTime,
                         Long fromClassroomId, Long toClassroomId) {
    }
}
