package ar.edu.utn.frc.siga.auth.scheduler;

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

    @Scheduled(cron = "${siga.jwt.refresh-purge-cron}", zone = "America/Argentina/Buenos_Aires")
    public void purgeExpired() {
        int deleted = refreshTokenService.purgeExpired();
        log.info("Cron de purga de refresh tokens vencidos disparado: eliminados={}", deleted);
    }
}
