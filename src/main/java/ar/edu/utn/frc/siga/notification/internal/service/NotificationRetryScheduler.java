package ar.edu.utn.frc.siga.notification.internal.service;

import ar.edu.utn.frc.siga.common.security.SystemScope;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NotificationRetryScheduler {

    private static final int BATCH_SIZE = 50;

    private final NotificationRetryClaimer claimer;
    private final NotificationDispatcher dispatcher;

    @Scheduled(fixedDelayString = "${siga.notifications.retry-interval}")
    void run() {
        SystemScope.run(() -> claimer.claimDue(BATCH_SIZE).forEach(dispatcher::dispatch));
    }
}
