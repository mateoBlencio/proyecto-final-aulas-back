package ar.edu.utn.frc.siga.auth.service.impl;

import ar.edu.utn.frc.siga.audit.AuditLabels;
import ar.edu.utn.frc.siga.audit.AuditOperation;
import ar.edu.utn.frc.siga.auth.dto.request.AssignRoleRequestDto;
import ar.edu.utn.frc.siga.auth.dto.response.RoleAssignmentDto;
import ar.edu.utn.frc.siga.auth.exception.RoleConflictException;
import ar.edu.utn.frc.siga.auth.exception.RoleDomainException;
import ar.edu.utn.frc.siga.auth.mapper.RoleAssignmentComposer;
import ar.edu.utn.frc.siga.auth.model.RoleAssignment;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.RoleAssignmentRepository;
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
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final BuildingService buildingService;
    private final RoleAssignmentComposer roleAssignmentComposer;

    @Override
    @Transactional
    @AuditOperation(value = "Asignación de rol", subject = "RoleAssignment")
    public RoleAssignmentDto assign(Long userId, AssignRoleRequestDto dto, String currentUserEmail) {
        log.debug("Asignando rol: userId={}, role={}, scopeType={}, scopeId={}",
                userId, dto.role(), dto.scopeType(), dto.scopeId());

        User user = findUserForUpdate(userId);
        validateRoleScope(dto.role(), dto.scopeType());
        validateScope(dto.scopeType(), dto.scopeId());
        List<RoleAssignment> existing = roleAssignmentRepository.findAllByUserId(userId);
        validateNotDuplicate(existing, dto);
        validateExclusivity(existing, dto.role());

        RoleAssignment assignment = RoleAssignment.builder()
                .user(user)
                .role(dto.role())
                .scopeType(dto.scopeType())
                .scopeId(dto.scopeId())
                .build();

        RoleAssignment saved = roleAssignmentRepository.save(assignment);
        log.info("Rol asignado: id={}, userId={}, role={}", saved.getId(), userId, dto.role());
        return roleAssignmentComposer.compose(saved);
    }

    @Override
    @Transactional
    @AuditOperation(value = "Revocación de rol", subject = "RoleAssignment")
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
            throw new RoleDomainException("Un usuario no puede revocar su propia asignación de rol");
        }

        roleAssignmentRepository.delete(assignment);
        log.info("Asignación revocada: id={}, userId={}", assignmentId, userId);
    }

    private void validateRoleScope(SystemRole role, ScopeType scopeType) {
        if (!role.allowsScope(scopeType)) {
            throw new RoleDomainException("El rol " + AuditLabels.constant(role) + " no admite el alcance " + AuditLabels.constant(scopeType));
        }
    }

    private void validateScope(ScopeType scopeType, Long scopeId) {
        if (scopeType == ScopeType.GLOBAL && scopeId != null) {
            throw new RoleDomainException("El alcance Global no admite scopeId");
        }
        if (scopeType == ScopeType.BUILDING) {
            if (scopeId == null) {
                throw new RoleDomainException("El alcance Edificio requiere un edificio");
            }
            buildingService.findById(scopeId);
        }
    }

    private void validateExclusivity(List<RoleAssignment> existing, SystemRole role) {
        if (role == SystemRole.ADMINISTRADOR && !existing.isEmpty()) {
            throw new RoleConflictException("El usuario ya tiene otros roles: un Administrador no puede tener "
                    + "Auxiliar áulico ni Consulta. Revocá primero esas asignaciones.");
        }
        if (role != SystemRole.ADMINISTRADOR
                && existing.stream().anyMatch(assignment -> assignment.getRole() == SystemRole.ADMINISTRADOR)) {
            throw new RoleConflictException("El usuario es Administrador: no admite otros roles.");
        }
    }

    private void validateNotDuplicate(List<RoleAssignment> existing, AssignRoleRequestDto dto) {
        boolean duplicate = existing.stream().anyMatch(assignment ->
                assignment.getRole() == dto.role()
                        && assignment.getScopeType() == dto.scopeType()
                        && Objects.equals(assignment.getScopeId(), dto.scopeId()));
        if (duplicate) {
            throw new RoleDomainException("El usuario ya tiene esa asignación de rol");
        }
    }

    private User findUserForUpdate(Long id) {
        return userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> {
                    log.warn("Usuario no encontrado: id={}", id);
                    return ResourceNotFoundException.of("User", id);
                });
    }
}
