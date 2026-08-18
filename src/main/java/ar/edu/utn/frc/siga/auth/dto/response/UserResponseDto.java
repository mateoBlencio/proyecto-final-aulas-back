package ar.edu.utn.frc.siga.auth.dto.response;

public record UserResponseDto(
        Integer id,
        String email,
        String rol) {
}
