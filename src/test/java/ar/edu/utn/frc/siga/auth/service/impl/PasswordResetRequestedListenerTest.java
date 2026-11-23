package ar.edu.utn.frc.siga.auth.service.impl;

import ar.edu.utn.frc.siga.auth.service.PasswordResetService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetRequestedListener")
class PasswordResetRequestedListenerTest {

    private static final String EMAIL = "ana.gomez@frc.utn.edu.ar";

    @Mock
    private PasswordResetService passwordResetService;

    private PasswordResetRequestedListener listener;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        listener = new PasswordResetRequestedListener(passwordResetService);
        logger = (Logger) LoggerFactory.getLogger(PasswordResetRequestedListener.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("delega en issueResetLink con el correo del evento")
    void delegatesToIssueResetLink() {
        listener.on(new PasswordResetRequested(EMAIL));

        verify(passwordResetService).issueResetLink(EMAIL);
    }

    @Test
    @DisplayName("traga la RuntimeException del servicio y la registra como error")
    void swallowsRuntimeExceptionAndLogsIt() {
        doThrow(new IllegalStateException("fallo del canal")).when(passwordResetService).issueResetLink(EMAIL);

        assertThatCode(() -> listener.on(new PasswordResetRequested(EMAIL))).doesNotThrowAnyException();

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("fallo del canal");
        });
    }
}
