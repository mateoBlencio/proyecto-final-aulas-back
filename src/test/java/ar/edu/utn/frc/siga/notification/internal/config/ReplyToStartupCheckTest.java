package ar.edu.utn.frc.siga.notification.internal.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReplyToStartupCheckTest {

    /** Los valores evitan subcadenas del ejemplo del mensaje ("casilla@frc.utn.edu.ar"), para que el
     *  doesNotContain del valor no dé un falso negativo. */
    @ParameterizedTest
    @ValueSource(strings = {
            "secacad-gestionaulas",
            "uno@frc.utn.edu.ar,dos@frc.utn.edu.ar",
            "Gestion Aulas <secacad-gestionaulas@frc.utn.edu.ar>",
            "docente@frc",
            "docente@frc.",
            "docente@.utn",
            "\"gestion aulas\"@frc.utn.edu.ar"
    })
    void rejectsAValueThatIsNotExactlyOneValidAddressWithoutEchoingIt(String replyTo) {
        ReplyToStartupCheck check = checkWith(replyTo);

        assertThatThrownBy(check::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tiene que ser una sola dirección de correo válida")
                .hasMessageNotContaining(replyTo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsBlankReplyToAsMissing(String replyTo) {
        assertThatThrownBy(checkWith(replyTo)::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Falta MAIL_REPLY_TO");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "secacad-gestionaulas@frc.utn.edu.ar",
            "  secacad-gestionaulas@frc.utn.edu.ar  ",
            "\tsecacad-gestionaulas@frc.utn.edu.ar\n"
    })
    void acceptsOneValidAddressEvenWithSurroundingWhitespace(String replyTo) {
        assertThatCode(checkWith(replyTo)::afterPropertiesSet).doesNotThrowAnyException();
    }

    private static ReplyToStartupCheck checkWith(String replyTo) {
        NotificationProperties properties = new NotificationProperties();
        properties.getEmail().setReplyTo(replyTo);
        return new ReplyToStartupCheck(properties);
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void failsStartupWhenMailIsEnabledAndReplyToIsEmpty() {
        runner.withPropertyValues("siga.notifications.email.enabled=true", "siga.notifications.email.reply-to=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("Falta MAIL_REPLY_TO");
                });
    }

    @Test
    void failsStartupWhenMailIsEnabledAndReplyToListsTwoAddresses() {
        runner.withPropertyValues("siga.notifications.email.enabled=true",
                        "siga.notifications.email.reply-to=uno@frc.utn.edu.ar,dos@frc.utn.edu.ar")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .hasMessageContaining("tiene que ser una sola dirección de correo válida");
                });
    }

    @Test
    void startsWhenMailIsEnabledAndReplyToIsSet() {
        runner.withPropertyValues("siga.notifications.email.enabled=true",
                        "siga.notifications.email.reply-to=secacad-gestionaulas@frc.utn.edu.ar")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(ReplyToStartupCheck.class));
    }

    @Test
    void startsWithoutReplyToWhenMailIsDisabled() {
        runner.withPropertyValues("siga.notifications.email.enabled=false", "siga.notifications.email.reply-to=")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ReplyToStartupCheck.class));
    }

    @Configuration
    @EnableConfigurationProperties(NotificationProperties.class)
    @Import(ReplyToStartupCheck.class)
    static class TestConfig {
    }
}
