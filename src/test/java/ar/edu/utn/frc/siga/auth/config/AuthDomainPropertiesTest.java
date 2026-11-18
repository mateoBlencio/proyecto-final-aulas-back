package ar.edu.utn.frc.siga.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("AuthDomainProperties")
class AuthDomainPropertiesTest {

    private final AuthDomainProperties properties = new AuthDomainProperties();

    @Test
    @DisplayName("normalizeEmail con null devuelve null")
    void normalizeEmailNull() {
        assertThat(AuthDomainProperties.normalizeEmail(null)).isNull();
    }

    @ParameterizedTest(name = "normalizeEmail(\"{0}\") = {1}")
    @CsvSource(delimiter = '|', ignoreLeadingAndTrailingWhitespace = false, value = {
            "jperez@sistemas.frc.utn.edu.ar|jperez@sistemas.frc.utn.edu.ar",
            "  jperez@frc.utn.edu.ar  |jperez@frc.utn.edu.ar",
            "\tJPerez@Sistemas.FRC.utn.edu.ar \t|jperez@sistemas.frc.utn.edu.ar",
            "JPEREZ@FRC.UTN.EDU.AR|jperez@frc.utn.edu.ar"
    })
    @DisplayName("normalizeEmail quita espacios de los extremos y pasa a minúsculas")
    void normalizeEmailStripsAndLowercases(String raw, String expected) {
        assertThat(AuthDomainProperties.normalizeEmail(raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "acepta jperez@{0}")
    @ValueSource(strings = {
            "frc.utn.edu.ar",
            "cbasicas.frc.utn.edu.ar",
            "civil.frc.utn.edu.ar",
            "colegio.frc.utn.edu.ar",
            "computos.frc.utn.edu.ar",
            "decanato.frc.utn.edu.ar",
            "egresado.frc.utn.edu.ar",
            "electrica.frc.utn.edu.ar",
            "electronica.frc.utn.edu.ar",
            "extension.frc.utn.edu.ar",
            "industrial.frc.utn.edu.ar",
            "licenciatura.frc.utn.edu.ar",
            "mecanica.frc.utn.edu.ar",
            "metalurgica.frc.utn.edu.ar",
            "nuclear.frc.utn.edu.ar",
            "org.frc.utn.edu.ar",
            "posgrado.frc.utn.edu.ar",
            "punilla.frc.utn.edu.ar",
            "quimica.frc.utn.edu.ar",
            "radio.frc.utn.edu.ar",
            "sa.frc.utn.edu.ar",
            "sae.frc.utn.edu.ar",
            "scdt.frc.utn.edu.ar",
            "sistemas.frc.utn.edu.ar",
            "tecnicatura.frc.utn.edu.ar",
            "virtual.frc.utn.edu.ar"
    })
    @DisplayName("acepta el dominio base y cada uno de los 25 subdominios institucionales")
    void aceptaDominioBaseYSubdominios(String domain) {
        assertThat(properties.isAllowedEmail("jperez@" + domain)).isTrue();
    }

    @ParameterizedTest(name = "acepta {0}")
    @ValueSource(strings = {
            "Jperez@Sistemas.FRC.utn.edu.ar",
            "JPEREZ@FRC.UTN.EDU.AR",
            "jperez@Virtual.Frc.Utn.Edu.Ar"
    })
    @DisplayName("ignora mayúsculas en el dominio")
    void aceptaDominioEnMayusculasYMinusculas(String email) {
        assertThat(properties.isAllowedEmail(email)).isTrue();
    }

    @ParameterizedTest(name = "rechaza \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {
            "jperez",
            "jperez@",
            "jperez@gmail.com",
            "jperez@utn.edu.ar",
            "jperez@frc.utn.edu.ar.evil.com",
            "x@evil.sistemas.frc.utn.edu.ar",
            "x@otro.frc.utn.edu.ar",
            "x@sistemasfrc.utn.edu.ar",
            "x@frc.utn.edu.ar@gmail.com",
            "@frc.utn.edu.ar",
            "a@b@frc.utn.edu.ar",
            "a@b@sistemas.frc.utn.edu.ar"
    })
    @DisplayName("rechaza emails sin dominio, sin parte local, con más de una @ o con dominio fuera de la lista")
    void rechazaDominiosNoInstitucionales(String email) {
        assertThat(properties.isAllowedEmail(email)).isFalse();
    }
}
