package ar.edu.utn.frc.siga.notification.internal.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
class NotificationDispatchListener {

    private final NotificationDispatcher dispatcher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onNotificationsQueued(NotificationsQueuedEvent event) {
        event.notificationIds().forEach(dispatcher::dispatch);
    }
}
