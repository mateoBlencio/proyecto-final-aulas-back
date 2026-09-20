package ar.edu.utn.frc.siga.notification.internal.model;

import ar.edu.utn.frc.siga.common.model.TimestampedEntity;
import ar.edu.utn.frc.siga.notification.api.NotificationChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "notificacion")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Notification extends TimestampedEntity {

    private static final int MAX_ERROR_LENGTH = 500;

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_notificacion")
    private Long id;

    @Column(name = "plantilla", nullable = false, length = 60)
    private String template;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, length = 30)
    private NotificationChannel channel;

    @Column(name = "destinatario", nullable = false, length = 150)
    private String recipient;

    @Column(name = "destinatario_nombre", length = 150)
    private String recipientName;

    @Column(name = "asunto", nullable = false)
    private String subject;

    @Column(name = "cuerpo", nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "intentos", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "proximo_intento")
    private Instant nextAttemptAt;

    @Column(name = "ultimo_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "clave_idempotencia", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "solicitado_por", length = 150)
    private String requestedBy;

    @Column(name = "fecha_envio")
    private Instant sentAt;

    public void markSent(Instant now) {
        this.status = NotificationStatus.SENT;
        this.sentAt = now;
        this.lastError = null;
    }

    public void markDiscarded(String reason) {
        this.status = NotificationStatus.DISCARDED;
        this.lastError = truncate(reason);
    }

    public void registerFailure(String error, Duration backoff, int maxAttempts) {
        this.attempts++;
        this.lastError = truncate(error);
        if (this.attempts >= maxAttempts) {
            this.status = NotificationStatus.FAILED;
            this.nextAttemptAt = null;
        } else {
            this.nextAttemptAt = Instant.now().plus(backoff);
        }
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
    }
}
