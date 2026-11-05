package ar.edu.utn.frc.siga.roomrequest.scheduler;

import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestExpiryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoomRequestAcceptanceScheduler {

    private final RoomRequestExpiryService expiryService;

    @Scheduled(cron = "${siga.room-requests.acceptance.cron}", zone = "America/Argentina/Buenos_Aires")
    public void acceptOverdueNotifiedItems() {
        log.info("Cron de aceptación automática de pedidos de aula disparado");
        expiryService.acceptOverdueNotifiedItems();
    }
}
