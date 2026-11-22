package ar.edu.utn.frc.siga.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class InformalRegisterTest {

    @ParameterizedTest
    @ValueSource(strings = {"Para tu pedido", "TE sirve", "Respondé este correo", "Si no nos respondés",
            "Pediste 3 aulas", "respondé este mail", "Mail", "que podés ver en SIGA", "No respondas este correo"})
    void detectsTuteoVoseoAndMail(String text) {
        assertThat(InformalRegister.find(text)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"estudiante", "tuteo", "Después de esa clase", "Pabellón Central", "contenido",
            "Le solicitamos que confirme", "responda este correo", "Estimado/a", "mailing", "que puede ver en SIGA"})
    void ignoresWordsThatOnlyContainThoseLetters(String text) {
        assertThat(InformalRegister.find(text)).isEmpty();
    }

    @Test
    void visibleTextDropsCssAndTagsAndKeepsAltText() {
        String html = "<html><head><style>.mail { color: red; }</style></head>"
                + "<body><img src=\"cid:x\" alt=\"Logo para tu correo\"><p class=\"mail\">Estimado/a:</p></body></html>";

        String visible = InformalRegister.visibleText(html);

        assertThat(visible).contains("Estimado/a:").contains("Logo para tu correo")
                .doesNotContain("color").doesNotContain("class");
        assertThat(InformalRegister.find(visible)).contains("tu");
    }
}
