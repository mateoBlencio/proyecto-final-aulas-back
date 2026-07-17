package ar.edu.utn.frc.siga.auth.controller;

import ar.edu.utn.frc.siga.auth.dto.request.CreateUserRequestDto;
import ar.edu.utn.frc.siga.auth.dto.request.UpdateUserEnabledRequestDto;
import ar.edu.utn.frc.siga.auth.dto.request.UpdateUserRoleRequestDto;
import ar.edu.utn.frc.siga.auth.dto.response.UserResponseDto;
import ar.edu.utn.frc.siga.auth.security.SecurityUser;
import ar.edu.utn.frc.siga.auth.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administración de usuarios: alta, habilitación/inhabilitación, cambio de rol y listados.
 */
@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    public ResponseEntity<UserResponseDto> create(@Valid @RequestBody CreateUserRequestDto dto) {
        log.debug("POST /v1/users: email={}", dto.email());
        UserResponseDto response = userService.create(dto);
        log.info("Usuario creado vía controller: id={}", response.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}/enabled")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    public ResponseEntity<UserResponseDto> setEnabled(@PathVariable Integer id,
                                                      @Valid @RequestBody UpdateUserEnabledRequestDto dto) {
        log.debug("PATCH /v1/users/{}/enabled: enabled={}", id, dto.enabled());
        UserResponseDto response = userService.setEnabled(id, dto.enabled());
        log.info("Estado de usuario actualizado vía controller: id={}, enabled={}", response.id(), dto.enabled());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    public ResponseEntity<UserResponseDto> changeRole(@PathVariable Integer id,
                                                      @Valid @RequestBody UpdateUserRoleRequestDto dto,
                                                      @AuthenticationPrincipal SecurityUser principal) {
        log.debug("PATCH /v1/users/{}/role: rol={}", id, dto.rol());
        UserResponseDto response = userService.changeRole(id, dto.rol(), principal.getEmail());
        log.info("Rol de usuario actualizado vía controller: id={}, rol={}", response.id(), dto.rol());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
    public ResponseEntity<Page<UserResponseDto>> findEnabled(
            @PageableDefault(size = 15, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        log.debug("GET /v1/users: page={}", pageable.getPageNumber());
        Page<UserResponseDto> page = userService.findEnabled(pageable);
        log.info("Usuarios habilitados listados: total={}", page.getTotalElements());
        return ResponseEntity.ok(page);
    }

    @GetMapping("/disabled")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    public ResponseEntity<Page<UserResponseDto>> findDisabled(
            @PageableDefault(size = 15, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        log.debug("GET /v1/users/disabled: page={}", pageable.getPageNumber());
        Page<UserResponseDto> page = userService.findDisabled(pageable);
        log.info("Usuarios inhabilitados listados: total={}", page.getTotalElements());
        return ResponseEntity.ok(page);
    }
}
