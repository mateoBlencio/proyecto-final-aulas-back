package ar.edu.utn.frc.siga.notification.internal.channel;

import ar.edu.utn.frc.siga.notification.api.NotificationChannel;
import ar.edu.utn.frc.siga.notification.api.NotificationRecipient;
import ar.edu.utn.frc.siga.notification.internal.config.NotificationProperties;
import ar.edu.utn.frc.siga.notification.internal.render.RenderedNotification;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.notifications.email", name = "enabled", havingValue = "true")
public class EmailChannelSender implements ChannelSender {

    private static final Pattern HEADER_INJECTION_CHARS = Pattern.compile("[\r\n]");
    private static final Pattern EMAIL_FORMAT = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Map<String, String> INLINE_IMAGES = Map.of(
            "logoSiga", "notifications/assets/logo-siga.png",
            "logoUtnFrc", "notifications/assets/logo-utn-frc.png");

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(RenderedNotification message, NotificationRecipient recipient) {
        String address = recipient.email();
        if (!isValidAddress(address)) {
            throw new IllegalArgumentException(
                    "Dirección de destino con formato inválido: " + mask(address));
        }

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(properties.getEmail().getFrom());
            helper.setTo(address);
            helper.setSubject(stripHeaderInjection(message.subject()));
            helper.setText(message.body(), true);
            for (Map.Entry<String, String> image : INLINE_IMAGES.entrySet()) {
                if (message.body().contains("cid:" + image.getKey())) {
                    helper.addInline(image.getKey(), new ClassPathResource(image.getValue()));
                }
            }
        } catch (MessagingException e) {
            throw new IllegalStateException("No se pudo armar el mail para " + mask(address), e);
        }

        try {
            mailSender.send(mimeMessage);
        } catch (MailException e) {
            throw new IllegalStateException("No se pudo enviar el mail a " + mask(address), e);
        }
        log.info("Mail enviado a {}", mask(address));
    }

    private static boolean isValidAddress(String address) {
        return address != null
                && !HEADER_INJECTION_CHARS.matcher(address).find()
                && EMAIL_FORMAT.matcher(address).matches();
    }

    private static String stripHeaderInjection(String value) {
        return value == null ? "" : HEADER_INJECTION_CHARS.matcher(value).replaceAll("");
    }

    private static String mask(String address) {
        if (address == null) {
            return "null";
        }
        int at = address.indexOf('@');
        if (at <= 1) {
            return "***" + address.substring(Math.max(at, 0));
        }
        return address.charAt(0) + "***" + address.substring(at);
    }
}
