package ar.edu.utn.frc.siga.notification.internal.channel;

import ar.edu.utn.frc.siga.notification.api.NotificationChannel;
import ar.edu.utn.frc.siga.notification.api.NotificationRecipient;
import ar.edu.utn.frc.siga.notification.internal.config.EmailAddressFormat;
import ar.edu.utn.frc.siga.notification.internal.config.NotificationProperties;
import ar.edu.utn.frc.siga.notification.internal.render.RenderedNotification;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.notifications.email", name = "enabled", havingValue = "true")
public class EmailChannelSender implements ChannelSender {

    private static final Pattern HEADER_INJECTION_CHARS = Pattern.compile("[\r\n]");
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
        if (!EmailAddressFormat.isValid(address)) {
            throw new IllegalArgumentException(
                    "Dirección de destino con formato inválido: " + mask(address));
        }

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper =
                    new MimeMessageHelper(mimeMessage, MimeMessageHelper.MULTIPART_MODE_RELATED, "UTF-8");
            helper.setFrom(properties.getEmail().getFrom());
            helper.setTo(address);
            helper.setSubject(stripHeaderInjection(message.subject()));
            helper.setText(message.body(), true);
            setReplyTo(mimeMessage, message.replyTo());
            InternetAddress[] cc = validAddresses(message.cc(), "CC");
            if (cc.length > 0) {
                helper.setCc(cc);
            }
            InternetAddress[] bcc = validAddresses(message.bcc(), "CCO");
            if (bcc.length > 0) {
                helper.setBcc(bcc);
            }
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
            // La causa distingue un 535 de autenticación, un certificado rechazado o un timeout en ultimo_error.
            // El servidor puede repetir el destinatario ("550 5.1.1 <docente@...>"): se enmascara igual.
            Throwable cause = e.getMostSpecificCause();
            // Lo mismo vale para las copias: el servidor también puede repetir una dirección de CC o CCO.
            String detail = cause.getMessage();
            if (detail != null) {
                List<String> echoed = new ArrayList<>(List.of(address));
                echoed.addAll(message.cc());
                echoed.addAll(message.bcc());
                for (String echo : echoed) {
                    if (echo.isBlank()) {
                        continue;
                    }
                    detail = Pattern.compile(Pattern.quote(echo.strip()), Pattern.CASE_INSENSITIVE).matcher(detail)
                            .replaceAll(Matcher.quoteReplacement(mask(echo.strip())));
                }
            }
            // Sin encadenar la causa: su mensaje trae la dirección sin enmascarar y saldría en el stack trace.
            throw new IllegalStateException("No se pudo enviar el mail a " + mask(address) + ": "
                    + cause.getClass().getSimpleName() + ": " + detail);
        }
        log.info("Mail enviado a {}", mask(address));
    }

    private static void setReplyTo(MimeMessage mimeMessage, List<String> replyTo) throws MessagingException {
        InternetAddress[] addresses = validAddresses(replyTo, "Reply-To");
        if (addresses.length > 0) {
            mimeMessage.setReplyTo(addresses);
        }
    }

    /** Las direcciones con formato válido; las inválidas se descartan con un aviso y no frenan el envío. */
    private static InternetAddress[] validAddresses(List<String> candidates, String header) {
        List<InternetAddress> addresses = new ArrayList<>();
        for (String address : candidates) {
            Optional<InternetAddress> parsed = EmailAddressFormat.parse(address.strip());
            if (parsed.isPresent()) {
                addresses.add(parsed.get());
            } else {
                log.warn("{} descartado por dirección inválida: {}", header, mask(address.strip()));
            }
        }
        return addresses.toArray(InternetAddress[]::new);
    }

    private static String stripHeaderInjection(String value) {
        return value == null ? "" : HEADER_INJECTION_CHARS.matcher(value).replaceAll("");
    }

    private static String mask(String address) {
        if (address == null) {
            return "null";
        }
        int at = address.indexOf('@');
        if (at < 0) {
            return "***";
        }
        if (at <= 1) {
            return "***" + address.substring(at);
        }
        return address.charAt(0) + "***" + address.substring(at);
    }
}
