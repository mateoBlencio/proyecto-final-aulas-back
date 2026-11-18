package ar.edu.utn.frc.siga.notification.internal.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EmailAddressFormatTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "secacad-gestionaulas@frc.utn.edu.ar",
            "a@b.co",
            "nombre.apellido+aulas@frc.utn.edu.ar"
    })
    void acceptsASingleBareAddressWithDottedDomain(String address) {
        assertThat(EmailAddressFormat.isValid(address)).isTrue();
        assertThat(EmailAddressFormat.parse(address)).hasValueSatisfying(parsed -> {
            assertThat(parsed.getAddress()).isEqualTo(address);
            assertThat(parsed.getPersonal()).isNull();
        });
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "\"gestion aulas\"@frc.utn.edu.ar",
            "secacad-gestionaulas",
            "uno@frc.utn.edu.ar,dos@frc.utn.edu.ar",
            "Gestion Aulas <secacad-gestionaulas@frc.utn.edu.ar>",
            "docente@frc",
            "docente@frc.",
            "docente@.utn",
            "@frc.utn.edu.ar",
            "doble@arroba@frc.utn.edu.ar",
            "docente@frc.utn.edu.ar\nBcc: externo@frc.utn.edu.ar",
            " docente@frc.utn.edu.ar",
            "aux..doble@frc.utn.edu.ar"
    })
    void rejectsAnythingThatIsNotExactlyOneBareAddress(String address) {
        assertThat(EmailAddressFormat.isValid(address)).isFalse();
        assertThat(EmailAddressFormat.parse(address)).isEmpty();
    }

    @Test
    void quotedLocalPartWithSpaceIsRejectedAlthoughJakartaMailWouldAcceptIt() throws Exception {
        String quoted = "\"gestion aulas\"@frc.utn.edu.ar";

        assertThat(new jakarta.mail.internet.InternetAddress(quoted, true).getAddress()).isEqualTo(quoted);
        assertThat(EmailAddressFormat.isValid(quoted)).isFalse();
    }
}
