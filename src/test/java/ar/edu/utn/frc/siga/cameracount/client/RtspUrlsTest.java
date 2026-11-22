package ar.edu.utn.frc.siga.cameracount.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RtspUrls: enmascarado de URLs RTSP para logs")
class RtspUrlsTest {

    @Test
    @DisplayName("con usuario y clave: oculta las credenciales y la ruta, conserva scheme, host y puerto")
    void masksUserAndPassword() {
        String masked = RtspUrls.mask("rtsp://admin:s3cr3t@172.16.1.10:554/Streaming/Channels/101");

        assertThat(masked).isEqualTo("rtsp://***@172.16.1.10:554/...");
    }

    @Test
    @DisplayName("sin puerto: no inventa uno")
    void keepsMissingPortMissing() {
        assertThat(RtspUrls.mask("rtsp://admin:s3cr3t@172.16.1.10/Streaming/Channels/101"))
                .isEqualTo("rtsp://***@172.16.1.10/...");
    }

    @Test
    @DisplayName("sin credenciales: igual antepone el marcador")
    void masksUrlWithoutCredentials() {
        assertThat(RtspUrls.mask("rtsp://172.16.1.10:554/x")).isEqualTo("rtsp://***@172.16.1.10:554/...");
    }

    @Test
    @DisplayName("IPv6: conserva los corchetes del host")
    void keepsIpv6Brackets() {
        assertThat(RtspUrls.mask("rtsp://u:p@[::1]:554/x")).isEqualTo("rtsp://***@[::1]:554/...");
    }

    @Test
    @DisplayName("scheme y host en mayúsculas salen en minúsculas")
    void lowercasesSchemeAndHost() {
        assertThat(RtspUrls.mask("RTSP://admin:s3cr3t@CAM.Local:554/x")).isEqualTo("rtsp://***@cam.local:554/...");
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" -> ***")
    @NullSource
    @ValueSource(strings = {
            "no es una url",
            "",
            "172.16.1.10:554/x",
            "rtsp:///x",
            "rtsp:camara",
            "rtsp://:554/x"
    })
    @DisplayName("null, texto que no parsea o URL sin host: devuelve solo ***")
    void returnsMarkerWhenUrlHasNoHost(String url) {
        assertThat(RtspUrls.mask(url)).isEqualTo("***");
    }

    @Test
    @DisplayName("clave con @ sin escapar: el host no se puede determinar y devuelve *** sin filtrar la clave")
    void doesNotLeakPasswordWithRawAtSign() {
        String masked = RtspUrls.mask("rtsp://admin:p@ss@172.16.1.10:554/x");

        assertThat(masked).isEqualTo("***").doesNotContain("p@ss", "admin");
    }
}
