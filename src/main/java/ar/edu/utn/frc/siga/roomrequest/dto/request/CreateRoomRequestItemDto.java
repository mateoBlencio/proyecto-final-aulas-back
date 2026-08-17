package ar.edu.utn.frc.siga.roomrequest.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Un pedido concreto dentro de la solicitud.
 *
 * <p>El docente carga hora de inicio y hora de fin, que es lo natural en el
 * formulario; la entidad guarda duración. La traducción entre ambas formas es
 * conocimiento del formulario, así que vive acá y no en el service.
 */
public record CreateRoomRequestItemDto(
        Long commissionId,
        @NotNull @FutureOrPresent LocalDate date,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull @Min(0) Integer enrolled,
        @NotNull @Min(0) Integer estimated,
        @NotNull @Min(1) Integer classroomCount,
        Integer currentClassroomId,
        Boolean requiresProjector,
        Boolean requiresComputers,
        @Min(1) Integer computerCount,
        Boolean requiresExamUsers,
        @Size(max = 255) String requiredSoftware,
        @Size(max = 1000) String observations,
        List<Integer> preferredClassroomIds
) {

    /**
     * Normaliza lo que el formulario deja opcional, para que nadie aguas abajo
     * tenga que preguntar por null: sin preferencias es una lista vacía, no
     * ausencia. La copia es defensiva y admite elementos null a propósito —
     * un id inexistente lo rechaza {@code RoomRequestValidator} con un 404
     * claro, que es mejor error que uno de deserialización.
     */
    public CreateRoomRequestItemDto {
        preferredClassroomIds = preferredClassroomIds == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(preferredClassroomIds));
    }

    /**
     * Duración del pedido, derivada del rango que carga el docente. Sólo tiene
     * sentido con el DTO ya validado: {@link #isTimeRangeValid()} garantiza que
     * ambas horas están presentes y que el rango es positivo.
     */
    public Duration duration() {
        return Duration.between(startTime, endTime);
    }

    @AssertTrue(message = "La hora de fin debe ser posterior a la hora de inicio")
    private boolean isTimeRangeValid() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }

    @AssertTrue(message = "Debe indicar la cantidad de computadoras si requiere computadoras, y omitirla si no")
    private boolean isComputerCountValid() {
        return Boolean.TRUE.equals(requiresComputers) == (computerCount != null);
    }

    @AssertTrue(message = "Solo se puede indicar software requerido si requiere computadoras")
    private boolean isRequiredSoftwareValid() {
        return requiredSoftware == null || Boolean.TRUE.equals(requiresComputers);
    }

    @AssertTrue(message = "No se puede repetir un aula en las preferencias")
    private boolean isPreferencesValid() {
        return preferredClassroomIds.size() == preferredClassroomIds.stream().distinct().count();
    }
}
