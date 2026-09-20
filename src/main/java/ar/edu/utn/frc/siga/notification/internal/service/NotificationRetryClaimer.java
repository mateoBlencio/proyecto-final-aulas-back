package ar.edu.utn.frc.siga.notification.internal.service;

import ar.edu.utn.frc.siga.notification.internal.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
class NotificationRetryClaimer {

    private static final Duration CLAIM_LOCK_DURATION = Duration.ofMinutes(5);

    private final NotificationRepository repository;

    @Transactional
    List<Long> claimDue(int limit) {
        List<Long> ids = repository.findDueForRetry(limit);
        if (!ids.isEmpty()) {
            repository.postponeNextAttempt(ids, Instant.now().plus(CLAIM_LOCK_DURATION));
        }
        return ids;
    }
}
