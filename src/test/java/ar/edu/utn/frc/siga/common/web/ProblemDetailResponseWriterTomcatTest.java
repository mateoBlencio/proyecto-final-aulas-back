package ar.edu.utn.frc.siga.common.web;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cubre el charset del cuerpo por el socket real sin Docker ni contexto Spring: un Tomcat embebido con un
 * servlet que llama a {@link ProblemDetailResponseWriter}. MockHttpServletResponse no detecta el bug porque
 * codifica el Writer con el charset que le pongan; Tomcat usa ISO-8859-1 si el Content-Type no declara charset.
 * El test equivalente con la cadena de seguridad completa es {@code ProblemDetailCharsetIntegrationTest}.
 */
@DisplayName("ProblemDetailResponseWriter sobre un Tomcat real")
class ProblemDetailResponseWriterTomcatTest {

    private static final String DETAIL = "No tenés permiso para acceder a este recurso.";

    @TempDir
    private Path baseDir;

    private Tomcat tomcat;

    @BeforeEach
    void startTomcat() throws Exception {
        tomcat = new Tomcat();
        tomcat.setBaseDir(baseDir.toString());
        tomcat.setPort(0);
        tomcat.getConnector();
        Context context = tomcat.addContext("", baseDir.toString());
        Tomcat.addServlet(context, "problem", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
                ProblemDetailResponseWriter.write(request, response, HttpStatus.FORBIDDEN, "Forbidden", DETAIL);
            }
        });
        context.addServletMappingDecoded("/*", "problem");
        tomcat.start();
    }

    @AfterEach
    void stopTomcat() throws Exception {
        tomcat.stop();
        tomcat.destroy();
    }

    @Test
    @DisplayName("el cuerpo con tildes sale en UTF-8 por el socket (C3 A9, sin E9) y el Content-Type no declara ISO-8859-1")
    void bodyIsUtf8OnTheWire() throws Exception {
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + "/api/x"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

        byte[] body = response.body();
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(body).contains((byte) 0xC3, (byte) 0xA9).doesNotContain((byte) 0xE9);
        assertThat(new String(body, StandardCharsets.UTF_8)).contains("\"detail\":\"" + DETAIL + "\"");
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .startsWith("application/problem+json")
                .doesNotContainIgnoringCase("ISO-8859-1");
    }
}
