package ar.edu.utn.frc.siga.sysacad.internal.scheduler;

import ar.edu.utn.frc.siga.sysacad.internal.service.AutogestionRoomCheck;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"siga.sysacad.enabled", "siga.sysacad.autogestion.enabled"}, havingValue = "true")
public class AutogestionRoomCheckScheduler {

    private final AutogestionRoomCheck roomCheck;

    @Scheduled(cron = "${siga.sysacad.autogestion.check-cron}", zone = "America/Argentina/Buenos_Aires")
    public void check() {
        log.info("Chequeo de cambios de aula de autogestión disparado");
        try {
            roomCheck.check(LocalDate.now());
        } catch (RuntimeException e) {
            log.error("Falló el chequeo de cambios de aula de autogestión", e);
        }
    }
}
