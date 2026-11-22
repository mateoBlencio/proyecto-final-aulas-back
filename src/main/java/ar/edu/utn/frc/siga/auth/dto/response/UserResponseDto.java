package ar.edu.utn.frc.siga.auth.dto.response;

import java.util.List;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record UserResponseDto(
        Long id,
        String email,
        String firstName,
        String lastName,
        List<RoleAssignmentDto> roleAssignments) {
}
