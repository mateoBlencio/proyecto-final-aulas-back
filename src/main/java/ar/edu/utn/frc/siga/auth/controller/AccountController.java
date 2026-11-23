package ar.edu.utn.frc.siga.auth.controller;

import ar.edu.utn.frc.siga.auth.dto.request.ChangePasswordRequest;
import ar.edu.utn.frc.siga.auth.dto.request.UpdateProfileRequest;
import ar.edu.utn.frc.siga.auth.dto.response.MeResponseDto;
import ar.edu.utn.frc.siga.auth.dto.response.TokenResponse;
import ar.edu.utn.frc.siga.auth.security.SecurityUser;
import ar.edu.utn.frc.siga.auth.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${siga.api.base-path}/users/me")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Tag(name = "Cuenta propia", description = "Datos, perfil y contraseña del usuario autenticado")
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    @Operation(summary = "Mi cuenta", description = "Asignaciones de rol y permisos efectivos con su alcance por edificio.")
    public ResponseEntity<MeResponseDto> me(@AuthenticationPrincipal SecurityUser principal) {
        return ResponseEntity.ok(accountService.me(principal));
    }

    @PatchMapping
    @Operation(summary = "Editar mi nombre y apellido")
    public ResponseEntity<MeResponseDto> updateProfile(@Valid @RequestBody UpdateProfileRequest request,
                                                       @AuthenticationPrincipal SecurityUser principal) {
        return ResponseEntity.ok(accountService.updateProfile(principal, request));
    }

    @PostMapping("/password")
    @Operation(summary = "Cambiar mi contraseña",
            description = "Exige la contraseña actual, revoca los refresh tokens y devuelve una sesión nueva.")
    public ResponseEntity<TokenResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                        @AuthenticationPrincipal SecurityUser principal,
                                                        HttpServletRequest httpRequest) {
        return ResponseEntity.ok(accountService.changePassword(principal, request, httpRequest.getRemoteAddr()));
    }
}
