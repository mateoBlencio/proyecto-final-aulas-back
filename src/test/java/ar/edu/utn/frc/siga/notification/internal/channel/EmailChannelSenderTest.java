package ar.edu.utn.frc.siga.notification.internal.channel;

import ar.edu.utn.frc.siga.notification.api.NotificationRecipient;
import ar.edu.utn.frc.siga.notification.internal.config.NotificationProperties;
import ar.edu.utn.frc.siga.notification.internal.render.RenderedNotification;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.mail.Address;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.URLName;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
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
        RenderedNotification message = new RenderedNotification("Aula confirmada", "<p>cuerpo</p>", List.of());
        NotificationRecipient recipient = new NotificationRecipient(
                "Docente", "docente@frc.utn.edu.ar\nBcc: externo@mail.com");

        assertThatThrownBy(() -> sender.send(message, recipient))
                .isInstanceOf(IllegalArgumentException.class);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void failureMessageCarriesTheSmtpCause() {
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
        org.mockito.Mockito.doThrow(new MailAuthenticationException(
                        new AuthenticationFailedException("535 5.7.8 authentication failed")))
                .when(mailSender).send(any(MimeMessage.class));
        RenderedNotification message = new RenderedNotification("Propuesta de aula", "<p>cuerpo</p>", List.of());

        assertThatThrownBy(() -> sender.send(message, new NotificationRecipient("Docente", "docente@frc.utn.edu.ar")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("d***@frc.utn.edu.ar")
                .hasMessageContaining("AuthenticationFailedException")
                .hasMessageContaining("535 5.7.8");
    }

    @Test
    void masksTheRecipientEchoedByTheSmtpServerRegardlessOfCase() {
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        org.mockito.Mockito.doThrow(new MailSendException(
                        Map.of(mimeMessage, new SendFailedException("550 5.1.1 <Docente@FRC.utn.edu.ar> unknown"))))
                .when(mailSender).send(any(MimeMessage.class));
        RenderedNotification message = new RenderedNotification("Propuesta de aula", "<p>cuerpo</p>", List.of());

        assertThatThrownBy(() -> sender.send(message, new NotificationRecipient("Docente", "docente@frc.utn.edu.ar")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("550 5.1.1 <d***@frc.utn.edu.ar> unknown")
                .hasNoCause()
                .satisfies(e -> assertThat(e.getMessage()).doesNotContainIgnoringCase("docente@frc.utn.edu.ar"));
    }

    /** Reproduce lo que lanza {@link JavaMailSenderImpl} cuando el servidor rechaza el RCPT: la MailSendException
     *  no trae causa encadenada, así que getMostSpecificCause() es ella misma, y su getMessage() concatena el
     *  toString() de cada SendFailedException con su excepción anidada, dirección incluida. */
    @Test
    void masksTheRecipientInsideTheFailureThatJavaMailSenderImplReallyThrows() throws Exception {
        String rejected = "550 5.1.1 <Docente@FRC.utn.edu.ar>: Recipient address rejected: User unknown";
        SendFailedException failure = new SendFailedException("Invalid Addresses",
                new SMTPAddressFailedException(new InternetAddress("Docente@FRC.utn.edu.ar"), "RCPT TO", 550, rejected),
                new Address[0], new Address[0], new Address[] {new InternetAddress("Docente@FRC.utn.edu.ar")});
        EmailChannelSender realSender = new EmailChannelSender(new RejectingMailSender(failure), properties);
        RenderedNotification message = new RenderedNotification("Propuesta de aula", "<p>cuerpo</p>", List.of());

        Throwable thrown = catchThrowable(
                () -> realSender.send(message, new NotificationRecipient("Docente", "docente@frc.utn.edu.ar")));

        assertThat(thrown).isInstanceOf(IllegalStateException.class).hasNoCause();
        assertThat(thrown.getSuppressed()).isEmpty();
        assertThat(thrown.getMessage())
                .contains("MailSendException")
                .contains("<d***@frc.utn.edu.ar>: Recipient address rejected");
        StringWriter stackTrace = new StringWriter();
        thrown.printStackTrace(new PrintWriter(stackTrace));
        assertThat(stackTrace.toString()).doesNotContainIgnoringCase("docente@frc.utn.edu.ar");
    }

    @Test
    void javaMailSenderImplPutsTheRawRecipientInAMailSendExceptionWithoutCause() throws Exception {
        SendFailedException failure = new SendFailedException("Invalid Addresses",
                new SMTPAddressFailedException(new InternetAddress("Docente@FRC.utn.edu.ar"), "RCPT TO", 550,
                        "550 5.1.1 <Docente@FRC.utn.edu.ar>: Recipient address rejected: User unknown"));
        JavaMailSenderImpl mailSenderImpl = new RejectingMailSender(failure);

        Throwable thrown = catchThrowable(() -> mailSenderImpl.send(mailSenderImpl.createMimeMessage()));

        assertThat(thrown).isInstanceOf(MailSendException.class).hasNoCause();
        assertThat(((MailSendException) thrown).getMostSpecificCause()).isSameAs(thrown);
        assertThat(thrown.getMessage()).contains("Docente@FRC.utn.edu.ar");
    }

    @Test
    void rejectsQuotedRecipientWithSpaceWithoutTouchingMailSender() {
        RenderedNotification message = new RenderedNotification("Propuesta de aula", "<p>cuerpo</p>", List.of());

        assertThatThrownBy(() -> sender.send(message,
                new NotificationRecipient("Gestión", "\"gestion aulas\"@frc.utn.edu.ar")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("gestion aulas");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void warnsWithMaskedAddressWhenDiscardingAQuotedReplyTo() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        RenderedNotification message = new RenderedNotification("Propuesta de aula", "<p>cuerpo</p>",
                List.of("\"gestion aulas\"@frc.utn.edu.ar", "aux2@frc.utn.edu.ar"));
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(EmailChannelSender.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            sender.send(message, new NotificationRecipient("Docente", "docente@frc.utn.edu.ar"));
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(mimeMessage.getReplyTo()).extracting(Object::toString).containsExactly("aux2@frc.utn.edu.ar");
        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage())
                        .isEqualTo("Reply-To descartado por dirección inválida: \"***@frc.utn.edu.ar"));
    }

    @Test
    void warnsWithOnlyStarsWhenDiscardingAReplyToWithoutAt() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        RenderedNotification message = new RenderedNotification("Propuesta de aula", "<p>cuerpo</p>",
                List.of("secacad-gestionaulas", "aux2@frc.utn.edu.ar"));
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(EmailChannelSender.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            sender.send(message, new NotificationRecipient("Docente", "docente@frc.utn.edu.ar"));
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(mimeMessage.getReplyTo()).extracting(Object::toString).containsExactly("aux2@frc.utn.edu.ar");
        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage())
                        .isEqualTo("Reply-To descartado por dirección inválida: ***")
                        .doesNotContain("secacad")
                        .doesNotContain("gestionaulas"));
    }

    @Test
    void sendsMailWithConfiguredFromAndRenderedSubjectForValidAddress() {
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        RenderedNotification message = new RenderedNotification("Aula confirmada — #158", "<p>cuerpo</p>", List.of());
        NotificationRecipient recipient = new NotificationRecipient("Docente", "docente@frc.utn.edu.ar");

        sender.send(message, recipient);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void setsEveryReplyToAddressOnTheMessage() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        RenderedNotification message = new RenderedNotification("Aula asignada", "<p>cuerpo</p>",
                List.of("aux1@frc.utn.edu.ar", "aux2@frc.utn.edu.ar"));

        sender.send(message, new NotificationRecipient("Docente", "docente@frc.utn.edu.ar"));

        assertThat(mimeMessage.getReplyTo()).extracting(Object::toString)
                .containsExactly("aux1@frc.utn.edu.ar", "aux2@frc.utn.edu.ar");
    }

    @Test
    void skipsInvalidReplyToAddressesAndKeepsTheValidOnes() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        RenderedNotification message = new RenderedNotification("Aula asignada", "<p>cuerpo</p>",
                List.of("no-es-un-mail", "aux2@frc.utn.edu.ar"));

        sender.send(message, new NotificationRecipient("Docente", "docente@frc.utn.edu.ar"));

        assertThat(mimeMessage.getReplyTo()).extracting(Object::toString)
                .containsExactly("aux2@frc.utn.edu.ar");
    }

    @Test
    void discardsReplyToThatPassesTheFormatFilterButFailsParsingAndStillSendsTheMail() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        RenderedNotification message = new RenderedNotification("Aula asignada", "<p>cuerpo</p>",
                List.of("aux..doble@frc.utn.edu.ar", "uno@frc.utn.edu.ar,dos@frc.utn.edu.ar", "aux2@frc.utn.edu.ar"));

        sender.send(message, new NotificationRecipient("Docente", "docente@frc.utn.edu.ar"));

        assertThat(mimeMessage.getReplyTo()).extracting(Object::toString)
                .containsExactly("aux2@frc.utn.edu.ar");
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendsTheMailWithoutReplyToWhenEveryReplyToAddressFailsParsing() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        RenderedNotification message = new RenderedNotification("Aula asignada", "<p>cuerpo</p>",
                List.of("aux..doble@frc.utn.edu.ar"));

        sender.send(message, new NotificationRecipient("Docente", "docente@frc.utn.edu.ar"));

        assertThat(mimeMessage.getHeader("Reply-To")).isNull();
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void attachesBothLogosInlineWhenBodyReferencesTheirCids() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        RenderedNotification message = new RenderedNotification(
                "Aula confirmada", "<img src=\"cid:logoSiga\"><img src=\"cid:logoUtnFrc\">", List.of());
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
        RenderedNotification message = new RenderedNotification("Aula confirmada", "<p>cuerpo sin logos</p>", List.of());
        NotificationRecipient recipient = new NotificationRecipient("Docente", "docente@frc.utn.edu.ar");

        sender.send(message, recipient);

        Object content = mimeMessage.getContent();
        if (content instanceof MimeMultipart multipart) {
            assertThat(contentIdsOf(multipart)).isEmpty();
        }
    }

    /** JavaMailSenderImpl real con un Transport que conecta sin red y rechaza el envío como lo haría el servidor. */
    private static final class RejectingMailSender extends JavaMailSenderImpl {

        private final MessagingException failure;

        RejectingMailSender(MessagingException failure) {
            this.failure = failure;
            setHost("localhost");
        }

        @Override
        protected Transport getTransport(Session session) {
            return new Transport(session, new URLName("smtp://localhost")) {
                @Override
                protected boolean protocolConnect(String host, int port, String user, String password) {
                    return true;
                }

                @Override
                public void sendMessage(Message msg, Address[] addresses) throws MessagingException {
                    throw failure;
                }
            };
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
