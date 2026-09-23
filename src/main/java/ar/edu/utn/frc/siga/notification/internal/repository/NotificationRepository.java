package ar.edu.utn.frc.siga.notification.internal.repository;

import ar.edu.utn.frc.siga.notification.api.NotificationChannel;
import ar.edu.utn.frc.siga.notification.internal.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByIdempotencyKeyAndChannelAndRecipient(
            String idempotencyKey, NotificationChannel channel, String recipient);

    long countByIdempotencyKey(String idempotencyKey);

    @Query(
            value = "SELECT id_notificacion FROM notificacion "
                    + "WHERE estado = 'PENDING' AND proximo_intento <= now() "
                    + "ORDER BY proximo_intento FOR UPDATE SKIP LOCKED LIMIT :limit",
            nativeQuery = true)
    List<Long> findDueForRetry(@Param("limit") int limit);

    @Query(
            value = "SELECT id_notificacion FROM notificacion "
                    + "WHERE id_notificacion IN (:ids) AND estado = 'PENDING' AND proximo_intento <= :now "
                    + "FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<Long> findDueByIds(@Param("ids") List<Long> ids, @Param("now") Instant now);

    @Modifying
    @Query(
            value = "UPDATE notificacion SET proximo_intento = :nextAttemptAt WHERE id_notificacion IN (:ids)",
            nativeQuery = true)
    void postponeNextAttempt(@Param("ids") List<Long> ids, @Param("nextAttemptAt") Instant nextAttemptAt);
}
