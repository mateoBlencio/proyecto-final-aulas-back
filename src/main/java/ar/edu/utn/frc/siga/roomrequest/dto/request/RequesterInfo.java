package ar.edu.utn.frc.siga.roomrequest.dto.request;

import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequesterInfo(
        @NotNull AcademicScope scope,
        @NotBlank @Size(max = 150) String teacherName,
        @NotBlank @Email @Size(max = 150) String teacherEmail,
        @NotBlank @Size(max = 40) String teacherPhone
) {}
