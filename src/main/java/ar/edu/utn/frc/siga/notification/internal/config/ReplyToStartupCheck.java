package ar.edu.utn.frc.siga.notification.internal.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Con el envío de mails habilitado ({@code MAIL_ENABLED=true}), el backend no arranca sin la casilla de
 *  respuesta ({@code MAIL_REPLY_TO}): sin ella, la respuesta del docente a un pedido derivado le llega solo a
 *  los auxiliares y Subsecretaría no se entera. Admite una sola casilla, con el formato de
 *  {@link EmailAddressFormat}: el envío descarta sin aviso un Reply-To que no lo cumple. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.notifications.email", name = "enabled", havingValue = "true")
class ReplyToStartupCheck implements InitializingBean {

    private final NotificationProperties properties;

    @Override
    public void afterPropertiesSet() {
        String replyTo = properties.getEmail().getReplyTo();
        if (replyTo == null || replyTo.isBlank()) {
            throw new IllegalStateException(
                    "Falta MAIL_REPLY_TO (siga.notifications.email.reply-to): con el envío de mails habilitado, "
                            + "todo mail sale con una casilla de respuesta. Cargala en el .env o poné MAIL_ENABLED=false.");
        }
        if (!EmailAddressFormat.isValid(replyTo.strip())) {
            throw new IllegalStateException(
                    "MAIL_REPLY_TO (siga.notifications.email.reply-to) tiene que ser una sola dirección de correo "
                            + "válida, sin nombre ni comas, por ejemplo casilla@frc.utn.edu.ar.");
        }
    }
}
