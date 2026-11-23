package ar.edu.utn.frc.siga.auth.service.impl;

import ar.edu.utn.frc.siga.auth.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class PasswordResetRequestedListener {

    private final PasswordResetService passwordResetService;

    @Async
    @EventListener
    void on(PasswordResetRequested event) {
        try {
            passwordResetService.issueResetLink(event.email());
        } catch (RuntimeException e) {
            log.error("No se pudo emitir el enlace de recuperación de contraseña", e);
        }
    }
}
