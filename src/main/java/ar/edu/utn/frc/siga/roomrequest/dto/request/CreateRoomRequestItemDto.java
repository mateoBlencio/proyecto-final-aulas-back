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

/** Un pedido dentro de la solicitud. El docente carga inicio/fin; la conversión a duración vive acá. */
public record CreateRoomRequestItemDto(
        Long commissionId,
        @NotNull @FutureOrPresent LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        @Min(0) Integer enrolled,
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

    /** Normaliza "sin preferencias" a lista vacía; un id inexistente lo rechaza el validator con un 404 claro. */
    public CreateRoomRequestItemDto {
        preferredClassroomIds = preferredClassroomIds == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(preferredClassroomIds));
    }

    /** Derivada del rango cargado; null si falta alguna hora. {@link #isTimeRangeValid()} asegura que el rango sea positivo. */
    public Duration duration() {
        return (startTime == null || endTime == null) ? null : Duration.between(startTime, endTime);
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
