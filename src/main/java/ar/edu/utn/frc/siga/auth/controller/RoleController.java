package ar.edu.utn.frc.siga.auth.controller;

import ar.edu.utn.frc.siga.auth.dto.request.CreateRoleRequestDto;
import ar.edu.utn.frc.siga.auth.dto.request.UpdateRolePermissionsRequestDto;
import ar.edu.utn.frc.siga.auth.dto.response.PermissionResponseDto;
import ar.edu.utn.frc.siga.auth.dto.response.RoleResponseDto;
import ar.edu.utn.frc.siga.auth.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Catálogo de roles y permisos, y administración de roles propios")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_ROLE_ASSIGN')")
    @Operation(summary = "Listar roles", description = "Devuelve todos los roles con su set de permisos.")
    public ResponseEntity<List<RoleResponseDto>> findAll() {
        log.debug("GET /v1/roles");
        return ResponseEntity.ok(roleService.findAll());
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
    @Operation(summary = "Listar catálogo de permisos",
               description = "Devuelve todos los permisos del sistema con su tipo de alcance (global o por edificio).")
    public ResponseEntity<List<PermissionResponseDto>> findAllPermissions() {
        log.debug("GET /v1/roles/permissions");
        return ResponseEntity.ok(roleService.findAllPermissions());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
    @Operation(summary = "Crear rol", description = "Crea un rol nuevo, siempre no-sistema.")
    public ResponseEntity<RoleResponseDto> create(@Valid @RequestBody CreateRoleRequestDto dto) {
        log.debug("POST /v1/roles: nombre={}", dto.name());
        RoleResponseDto response = roleService.create(dto);
        log.info("Rol creado vía controller: id={}", response.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
    @Operation(summary = "Actualizar permisos de un rol",
               description = "Reemplaza el set de permisos del rol indicado, sea de sistema o propio.")
    public ResponseEntity<RoleResponseDto> updatePermissions(
            @PathVariable Long id, @Valid @RequestBody UpdateRolePermissionsRequestDto dto) {
        log.debug("PATCH /v1/roles/{}/permissions", id);
        RoleResponseDto response = roleService.updatePermissions(id, dto.permissions());
        log.info("Permisos de rol actualizados vía controller: id={}", id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
    @Operation(summary = "Eliminar rol",
               description = "404 si no existe. 400 si es un rol de sistema o si tiene asignaciones activas.")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.debug("DELETE /v1/roles/{}", id);
        roleService.delete(id);
        log.info("Rol eliminado vía controller: id={}", id);
        return ResponseEntity.noContent().build();
    }
}
