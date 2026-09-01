package ar.edu.utn.frc.siga.roomrequest.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record ScheduledItemDto(
        @FutureOrPresent LocalDate date,
        DayOfWeek dayOfWeek,
        @Min(0) Integer estimated,
        @Min(1) @Max(100) Integer classroomCount,
        Boolean requiresProjector,
        Boolean requiresComputers,
        @Min(1) Integer computerCount,
        Boolean requiresExamUsers,
        @Size(max = 255) String requiredSoftware,
        @Size(max = 1000) String observations,
        List<Long> preferredClassroomIds
) implements CreateRoomRequestItemDto {

    public ScheduledItemDto {
        preferredClassroomIds = preferredClassroomIds == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(preferredClassroomIds));
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
