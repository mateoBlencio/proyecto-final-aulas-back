package ar.edu.utn.frc.siga.roomrequest.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pedido de un parcial fuera de horario de clases, un final, una conferencia o un "otro": el docente
 * carga <b>fecha</b> y <b>franja horaria</b>. {@code commissionId} lo interpreta cada handler
 * (obligatorio y distinto por ítem en el parcial fuera de horario; prohibido en el final; opcional en
 * conferencia y otro).
 */
public record FreeFormItemDto(
        Long commissionId,
        @NotNull @FutureOrPresent LocalDate date,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull @Min(0) Integer estimated,
        @Min(1) Integer classroomCount,
        Boolean requiresProjector,
        Boolean requiresComputers,
        @Min(1) Integer computerCount,
        Boolean requiresExamUsers,
        @Size(max = 255) String requiredSoftware,
        @Size(max = 1000) String observations,
        List<Long> preferredClassroomIds
) implements CreateRoomRequestItemDto {

    public FreeFormItemDto {
        preferredClassroomIds = preferredClassroomIds == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(preferredClassroomIds));
    }

    @AssertTrue(message = "La hora de fin debe ser posterior a la hora de inicio")
    private boolean isTimeRangeValid() {
        return RoomRequestItemChecks.timeRangeValid(startTime, endTime);
    }

    @AssertTrue(message = "Debe indicar la cantidad de computadoras si requiere computadoras, y omitirla si no")
    private boolean isComputerCountValid() {
        return RoomRequestItemChecks.computerCountConsistent(requiresComputers, computerCount);
    }

    @AssertTrue(message = "Solo se puede indicar software requerido si requiere computadoras")
    private boolean isRequiredSoftwareValid() {
        return RoomRequestItemChecks.requiredSoftwareConsistent(requiresComputers, requiredSoftware);
    }

    @AssertTrue(message = "No se puede repetir un aula en las preferencias")
    private boolean isPreferencesValid() {
        return RoomRequestItemChecks.preferencesDistinct(preferredClassroomIds);
    }
}
