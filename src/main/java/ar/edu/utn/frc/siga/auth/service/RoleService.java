package ar.edu.utn.frc.siga.auth.service;

import ar.edu.utn.frc.siga.auth.dto.request.CreateRoleRequestDto;
import ar.edu.utn.frc.siga.auth.dto.response.PermissionResponseDto;
import ar.edu.utn.frc.siga.auth.dto.response.RoleResponseDto;
import ar.edu.utn.frc.siga.common.security.Permission;

import java.util.List;
import java.util.Set;

public interface RoleService {

    List<RoleResponseDto> findAll();

    List<PermissionResponseDto> findAllPermissions();

    RoleResponseDto create(CreateRoleRequestDto dto);

    RoleResponseDto updatePermissions(Long id, Set<Permission> permissions);

    void delete(Long id);
}
