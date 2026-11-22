package ar.edu.utn.frc.siga.allocation.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ClassroomWithdrawalRequestDto(
        @NotNull Long classroomId,
        @FutureOrPresent LocalDate from,
        @Size(max = 255) String note
) {
}
