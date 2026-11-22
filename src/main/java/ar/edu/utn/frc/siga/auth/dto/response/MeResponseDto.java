package ar.edu.utn.frc.siga.auth.dto.response;

import java.util.List;

public record MeResponseDto(
        Long id,
        String email,
        String firstName,
        String lastName,
        List<RoleAssignmentDto> roleAssignments,
        List<PermissionGrantDto> permissions) {
}
