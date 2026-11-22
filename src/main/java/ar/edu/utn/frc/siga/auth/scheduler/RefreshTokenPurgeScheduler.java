package ar.edu.utn.frc.siga.auth.scheduler;

import ar.edu.utn.frc.siga.auth.service.PasswordResetService;
import ar.edu.utn.frc.siga.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenPurgeScheduler {

    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;

    @Scheduled(cron = "${siga.jwt.refresh-purge-cron}", zone = "America/Argentina/Buenos_Aires")
    public void purgeExpired() {
        int deleted = refreshTokenService.purgeExpired();
        int deletedResetTokens = passwordResetService.purgeExpired();
        log.info("Cron de purga de tokens vencidos disparado: refreshEliminados={}, recuperacionEliminados={}",
                deleted, deletedResetTokens);
    }
}
