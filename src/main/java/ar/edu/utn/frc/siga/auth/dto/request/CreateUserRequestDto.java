package ar.edu.utn.frc.siga.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateUserRequestDto(
        @NotBlank @Email String email,
        @NotBlank String password,
        @NotBlank String rol) {
}
