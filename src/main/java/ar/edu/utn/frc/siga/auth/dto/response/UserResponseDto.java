package ar.edu.utn.frc.siga.auth.dto.response;

public record UserResponseDto(
        Long id,
        String email,
        String rol) {
}
