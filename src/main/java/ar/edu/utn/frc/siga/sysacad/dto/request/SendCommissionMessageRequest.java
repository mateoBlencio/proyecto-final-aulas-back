package ar.edu.utn.frc.siga.sysacad.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SendCommissionMessageRequest(
        @NotNull Integer publicLegajo,
        @NotNull @Size(max = 10) String publicUserType,
        @NotNull LocalDate untilDate,
        @NotNull Integer academicYear,
        @NotNull Integer specialty,
        @NotBlank String courseCode,
        @NotBlank @Size(max = 150) String note) {
}
