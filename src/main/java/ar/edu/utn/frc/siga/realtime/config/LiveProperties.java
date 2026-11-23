package ar.edu.utn.frc.siga.realtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param maxConnectionDuration el back cierra cada conexión pasado este tiempo y el front reconecta con su token
 *                              vigente, así un token vencido o un permiso quitado no siguen recibiendo avisos
 * @param heartbeatInterval     comentario vacío para que proxies y balanceadores no corten una conexión ociosa
 * @param flushInterval         ventana en la que se juntan los cambios antes de avisar: un import de mil
 *                              asignaciones sale como un solo aviso
 * @param maxConnections        tope de conexiones abiertas a la vez
 */
@ConfigurationProperties(prefix = "siga.realtime")
public record LiveProperties(Duration maxConnectionDuration, Duration heartbeatInterval, Duration flushInterval,
                             int maxConnections) {

    public LiveProperties {
        if (maxConnectionDuration == null) maxConnectionDuration = Duration.ofMinutes(15);
        if (heartbeatInterval == null) heartbeatInterval = Duration.ofSeconds(25);
        if (flushInterval == null) flushInterval = Duration.ofMillis(500);
        if (maxConnections <= 0) maxConnections = 500;
    }
}
