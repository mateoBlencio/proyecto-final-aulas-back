package ar.edu.utn.frc.siga.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Default config (no trusted proxies): a direct client cannot spoof its IP with X-Forwarded-For. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Import(RemoteAddrCapture.class)
class ClientIpUntrustedPeerIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void remoteAddr_fromUntrustedPeer_ignoresXForwardedFor() throws Exception {
        ClientIpTrustedProxyIntegrationTest.call(String.valueOf(port), "203.0.113.7");

        assertThat(RemoteAddrCapture.LAST.get()).isIn("127.0.0.1", "0:0:0:0:0:0:0:1");
    }
}
