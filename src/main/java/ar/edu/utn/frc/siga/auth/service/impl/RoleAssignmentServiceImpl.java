package ar.edu.utn.frc.siga.auth.service.impl;

import ar.edu.utn.frc.siga.auth.dto.request.AssignRoleRequestDto;
import ar.edu.utn.frc.siga.auth.dto.response.RoleAssignmentDto;
import ar.edu.utn.frc.siga.auth.exception.RoleDomainException;
import ar.edu.utn.frc.siga.auth.mapper.RoleAssignmentComposer;
import ar.edu.utn.frc.siga.auth.model.Role;
import ar.edu.utn.frc.siga.auth.model.RoleAssignment;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.RoleAssignmentRepository;
import ar.edu.utn.frc.siga.auth.repository.RoleRepository;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.service.RoleAssignmentService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RoleAssignmentServiceImpl implements RoleAssignmentService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final BuildingService buildingService;
    private final RoleAssignmentComposer roleAssignmentComposer;

    @Override
    @Transactional
    public RoleAssignmentDto assign(Long userId, AssignRoleRequestDto dto, String currentUserEmail) {
        log.debug("Asignando rol: userId={}, roleId={}, scopeType={}, scopeId={}",
                userId, dto.roleId(), dto.scopeType(), dto.scopeId());

        User user = findUser(userId);
        Role role = findRole(dto.roleId());
        validateScope(dto.scopeType(), dto.scopeId());
        validateNotDuplicate(userId, dto);

        RoleAssignment assignment = RoleAssignment.builder()
                .user(user)
                .role(role)
                .scopeType(dto.scopeType())
                .scopeId(dto.scopeId())
                .build();

        RoleAssignment saved = roleAssignmentRepository.save(assignment);
        log.info("Rol asignado: id={}, userId={}, roleId={}", saved.getId(), userId, dto.roleId());
        return roleAssignmentComposer.compose(saved);
    }

    @Override
    @Transactional
    public void revoke(Long userId, Long assignmentId, String currentUserEmail) {
        log.debug("Revocando asignación: userId={}, assignmentId={}", userId, assignmentId);

        RoleAssignment assignment = roleAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> {
                    log.warn("Asignación no encontrada: id={}", assignmentId);
                    return ResourceNotFoundException.of("RoleAssignment", assignmentId);
                });

        if (!assignment.getUser().getId().equals(userId)) {
            log.warn("Asignación no encontrada para el usuario: id={}, userId={}", assignmentId, userId);
            throw ResourceNotFoundException.of("RoleAssignment", assignmentId);
        }

        if (assignment.getUser().getEmail().equalsIgnoreCase(currentUserEmail)) {
            log.warn("Auto-revocación rechazada: userId={}, assignmentId={}", userId, assignmentId);
            throw new RoleDomainException("A user cannot revoke their own role assignment");
        }

        roleAssignmentRepository.delete(assignment);
        log.info("Asignación revocada: id={}, userId={}", assignmentId, userId);
    }

    private void validateScope(ScopeType scopeType, Long scopeId) {
        if (scopeType == ScopeType.GLOBAL && scopeId != null) {
            throw new RoleDomainException("scopeId must be null for GLOBAL scope");
        }
        if (scopeType == ScopeType.BUILDING) {
            if (scopeId == null) {
                throw new RoleDomainException("scopeId is required for BUILDING scope");
            }
            buildingService.findById(scopeId);
        }
    }

    private void validateNotDuplicate(Long userId, AssignRoleRequestDto dto) {
        List<RoleAssignment> existing = roleAssignmentRepository.findAllByUserId(userId);
        boolean duplicate = existing.stream().anyMatch(assignment ->
                assignment.getRole().getId().equals(dto.roleId())
                        && assignment.getScopeType() == dto.scopeType()
                        && Objects.equals(assignment.getScopeId(), dto.scopeId()));
        if (duplicate) {
            throw new RoleDomainException("The user already has that role assignment");
        }
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Usuario no encontrado: id={}", id);
                    return ResourceNotFoundException.of("User", id);
                });
    }

    private Role findRole(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Rol no encontrado: id={}", id);
                    return ResourceNotFoundException.of("Role", id);
                });
    }
}
