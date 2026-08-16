package ar.edu.utn.frc.siga.roomrequest.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Un pedido concreto dentro de la solicitud.
 *
 * <p>El docente carga hora de inicio y hora de fin, que es lo natural en el
 * formulario; la conversión a duración se hace al armar la entidad.
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

        // Wrappers y no primitivos: omitir un booleano en el JSON tiene que
        // significar "no", no un 400. Con primitivos, Jackson rechaza el body
        // entero al no poder mapear el ausente. El default lo pone el service.
        Boolean requiresProjector,

        Boolean requiresComputers,

        @Min(1) Integer computerCount,

        Boolean requiresExamUsers,

        String requiredSoftware,

        String observations,

        List<Integer> preferredClassroomIds
) {

    @AssertTrue(message = "La hora de fin debe ser posterior a la hora de inicio")
    private boolean isTimeRangeValid() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }

    @AssertTrue(message = "Debe indicar la cantidad de computadoras si requiere computadoras, y omitirla si no")
    private boolean isComputerCountValid() {
        return Boolean.TRUE.equals(requiresComputers) == (computerCount != null);
    }

    @AssertTrue(message = "No se puede repetir un aula en las preferencias")
    private boolean isPreferencesValid() {
        return preferredClassroomIds == null
                || preferredClassroomIds.size() == preferredClassroomIds.stream().distinct().count();
    }
}
