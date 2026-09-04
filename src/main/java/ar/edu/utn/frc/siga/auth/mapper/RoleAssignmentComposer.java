package ar.edu.utn.frc.siga.auth.mapper;

import ar.edu.utn.frc.siga.auth.dto.response.RoleAssignmentDto;
import ar.edu.utn.frc.siga.auth.model.RoleAssignment;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RoleAssignmentComposer {

    private final UserMapper userMapper;
    private final BuildingService buildingService;

    public RoleAssignmentDto compose(RoleAssignment assignment) {
        RoleAssignmentDto base = userMapper.toDto(assignment);
        String scopeName = assignment.getScopeType() == ScopeType.BUILDING
                ? resolveBuildingName(assignment.getScopeId()) : null;
        return new RoleAssignmentDto(base.id(), base.roleName(), base.scopeType(), base.scopeId(), scopeName);
    }

    public List<RoleAssignmentDto> composeAll(List<RoleAssignment> assignments) {
        return assignments.stream().map(this::compose).toList();
    }

    private String resolveBuildingName(Long buildingId) {
        try {
            return buildingService.findById(buildingId).name();
        } catch (ResourceNotFoundException e) {
            return null;
        }
    }
}
