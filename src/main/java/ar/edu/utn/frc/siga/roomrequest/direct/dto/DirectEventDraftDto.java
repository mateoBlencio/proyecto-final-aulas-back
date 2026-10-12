package ar.edu.utn.frc.siga.roomrequest.direct.dto;

import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record DirectEventDraftDto(
        @NotNull RoomRequestType type,
        Long subjectId,
        Long commissionId,
        @NotNull @FutureOrPresent LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        @NotNull @Min(1) @Max(10_000) Integer estimated,
        @Min(1) @Max(100) Integer classroomCount,
        Boolean requiresProjector,
        Boolean requiresComputers,
        @Min(1) @Max(1_000) Integer computerCount,
        Boolean requiresExamUsers,
        @Size(max = 255) String requiredSoftware,
        @Size(max = 1000) String description
) {

    public int roomCount() {
        return classroomCount == null ? 1 : classroomCount;
    }

    @AssertTrue(message = "Debe indicar la cantidad de computadoras si requiere computadoras, y omitirla si no")
    private boolean isComputerCountValid() {
        return Boolean.TRUE.equals(requiresComputers) == (computerCount != null);
    }

    @AssertTrue(message = "Solo se puede indicar software requerido si requiere computadoras")
    private boolean isRequiredSoftwareValid() {
        return requiredSoftware == null || Boolean.TRUE.equals(requiresComputers);
    }
}
