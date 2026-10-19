package ar.edu.utn.frc.siga.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** The peer here (loopback) is a trusted proxy: the client IP comes from X-Forwarded-For. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.tomcat.remoteip.internal-proxies=127\\.0\\.0\\.1|0:0:0:0:0:0:0:1|::1")
@ActiveProfiles("integration")
@Import(RemoteAddrCapture.class)
class ClientIpTrustedProxyIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void remoteAddr_fromTrustedProxy_isTheRightmostUntrustedXForwardedForEntry() throws Exception {
        call("6.6.6.6, 203.0.113.7");

        assertThat(RemoteAddrCapture.LAST.get()).isEqualTo("203.0.113.7");
    }

    static void call(String port, String xForwardedFor) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/refresh"))
                .POST(HttpRequest.BodyPublishers.noBody());
        if (xForwardedFor != null) {
            request.header("X-Forwarded-For", xForwardedFor);
        }
        HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.discarding());
    }

    private void call(String xForwardedFor) throws Exception {
        call(String.valueOf(port), xForwardedFor);
    }
}
