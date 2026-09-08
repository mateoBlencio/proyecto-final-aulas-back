package ar.edu.utn.frc.siga.auth.service;

import ar.edu.utn.frc.siga.auth.dto.request.AssignRoleRequestDto;
import ar.edu.utn.frc.siga.auth.dto.response.RoleAssignmentDto;

public interface RoleAssignmentService {

    RoleAssignmentDto assign(Long userId, AssignRoleRequestDto dto, String currentUserEmail);

    void revoke(Long userId, Long assignmentId, String currentUserEmail);
}
