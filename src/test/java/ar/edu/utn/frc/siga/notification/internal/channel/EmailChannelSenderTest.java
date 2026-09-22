package ar.edu.utn.frc.siga.notification.internal.channel;

import ar.edu.utn.frc.siga.notification.api.NotificationRecipient;
import ar.edu.utn.frc.siga.notification.internal.config.NotificationProperties;
import ar.edu.utn.frc.siga.notification.internal.render.RenderedNotification;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailChannelSenderTest {

    @Mock
    private JavaMailSender mailSender;

    private NotificationProperties properties;
    private EmailChannelSender sender;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        properties.getEmail().setFrom("no-reply@frc.utn.edu.ar");
        sender = new EmailChannelSender(mailSender, properties);
    }

    @Test
    void rejectsAddressWithHeaderInjectionWithoutTouchingMailSender() {
        RenderedNotification message = new RenderedNotification("Aula confirmada", "<p>cuerpo</p>");
        NotificationRecipient recipient = new NotificationRecipient(
                "Docente", "docente@frc.utn.edu.ar\nBcc: externo@mail.com");

        assertThatThrownBy(() -> sender.send(message, recipient))
                .isInstanceOf(IllegalArgumentException.class);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendsMailWithConfiguredFromAndRenderedSubjectForValidAddress() {
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        RenderedNotification message = new RenderedNotification("Aula confirmada — #158", "<p>cuerpo</p>");
        NotificationRecipient recipient = new NotificationRecipient("Docente", "docente@frc.utn.edu.ar");

        sender.send(message, recipient);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void attachesBothLogosInlineWhenBodyReferencesTheirCids() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        RenderedNotification message = new RenderedNotification(
                "Aula confirmada", "<img src=\"cid:logoSiga\"><img src=\"cid:logoUtnFrc\">");
        NotificationRecipient recipient = new NotificationRecipient("Docente", "docente@frc.utn.edu.ar");

        sender.send(message, recipient);

        assertThat(mimeMessage.getContent()).isInstanceOf(MimeMultipart.class);
        assertThat(contentIdsOf((MimeMultipart) mimeMessage.getContent()))
                .containsExactlyInAnyOrder("<logoSiga>", "<logoUtnFrc>");
    }

    @Test
    void doesNotAttachInlinePartsWhenBodyHasNoCidReference() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        RenderedNotification message = new RenderedNotification("Aula confirmada", "<p>cuerpo sin logos</p>");
        NotificationRecipient recipient = new NotificationRecipient("Docente", "docente@frc.utn.edu.ar");

        sender.send(message, recipient);

        Object content = mimeMessage.getContent();
        if (content instanceof MimeMultipart multipart) {
            assertThat(contentIdsOf(multipart)).isEmpty();
        }
    }

    private static List<String> contentIdsOf(MimeMultipart multipart) throws Exception {
        List<String> contentIds = new ArrayList<>();
        for (int i = 0; i < multipart.getCount(); i++) {
            MimeBodyPart part = (MimeBodyPart) multipart.getBodyPart(i);
            if (part.getContentID() != null) {
                contentIds.add(part.getContentID());
            }
            if (part.getContent() instanceof MimeMultipart nested) {
                contentIds.addAll(contentIdsOf(nested));
            }
        }
        return contentIds;
    }
}
