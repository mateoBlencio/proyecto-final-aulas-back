package ar.edu.utn.frc.siga.auth.service.impl;

import ar.edu.utn.frc.siga.auth.dto.request.CreateRoleRequestDto;
import ar.edu.utn.frc.siga.auth.dto.response.PermissionResponseDto;
import ar.edu.utn.frc.siga.auth.dto.response.RoleResponseDto;
import ar.edu.utn.frc.siga.auth.exception.RoleDomainException;
import ar.edu.utn.frc.siga.auth.model.Role;
import ar.edu.utn.frc.siga.auth.repository.RoleAssignmentRepository;
import ar.edu.utn.frc.siga.auth.repository.RoleRepository;
import ar.edu.utn.frc.siga.auth.service.RoleService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.security.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;

    @Override
    public List<RoleResponseDto> findAll() {
        log.debug("Listando roles");
        return roleRepository.findAll().stream().map(this::toDto).toList();
    }

    @Override
    public List<PermissionResponseDto> findAllPermissions() {
        log.debug("Listando catálogo de permisos");
        return Arrays.stream(Permission.values())
                .map(permission -> new PermissionResponseDto(permission, permission.scopeType()))
                .toList();
    }

    @Override
    @Transactional
    public RoleResponseDto create(CreateRoleRequestDto dto) {
        log.debug("Creando rol: nombre={}", dto.name());
        if (roleRepository.existsByName(dto.name())) {
            log.warn("Alta de rol rechazada: nombre '{}' ya existe", dto.name());
            throw new RoleDomainException("Role name already exists: " + dto.name());
        }

        Role role = Role.builder()
                .name(dto.name())
                .systemRole(false)
                .permissions(dto.permissions())
                .build();

        Role saved = roleRepository.save(role);
        log.info("Rol creado: id={}, nombre={}", saved.getId(), saved.getName());
        return toDto(saved);
    }

    @Override
    @Transactional
    public RoleResponseDto updatePermissions(Long id, Set<Permission> permissions) {
        log.debug("Actualizando permisos de rol: id={}", id);
        Role role = findExisting(id);
        role.setPermissions(permissions);
        Role saved = roleRepository.save(role);
        log.info("Permisos de rol actualizados: id={}, count={}", id, permissions.size());
        return toDto(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        log.debug("Eliminando rol: id={}", id);
        Role role = findExisting(id);
        if (role.isSystemRole()) {
            log.warn("Baja de rol rechazada: id={} es un rol de sistema", id);
            throw new RoleDomainException("System roles cannot be deleted: " + role.getName());
        }
        if (roleAssignmentRepository.existsByRoleId(id)) {
            log.warn("Baja de rol rechazada: id={} está en uso", id);
            throw new RoleDomainException("Role is assigned to at least one user, cannot be deleted: " + role.getName());
        }
        roleRepository.delete(role);
        log.info("Rol eliminado: id={}", id);
    }

    private Role findExisting(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Rol no encontrado: id={}", id);
                    return ResourceNotFoundException.of("Role", id);
                });
    }

    private RoleResponseDto toDto(Role role) {
        return new RoleResponseDto(role.getId(), role.getName(), role.isSystemRole(), role.getPermissions());
    }
}
