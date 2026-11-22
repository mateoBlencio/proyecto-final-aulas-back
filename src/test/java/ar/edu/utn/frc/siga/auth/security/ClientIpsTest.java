package ar.edu.utn.frc.siga.auth.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClientIps")
class ClientIpsTest {

    @Test
    @DisplayName("IPv4 y null quedan igual")
    void ipv4AndNullAreUnchanged() {
        assertThat(ClientIps.normalize("203.0.113.9")).isEqualTo("203.0.113.9");
        assertThat(ClientIps.normalize(null)).isNull();
    }

    @Test
    @DisplayName("IPv6 se reduce al prefijo /64 en hexadecimal")
    void ipv6IsReducedToTheSixtyFourBitPrefix() {
        assertThat(ClientIps.normalize("2001:db8:1:2::1")).isEqualTo("v6:20010db800010002/64");
        assertThat(ClientIps.normalize("2001:db8:1:2:aaaa:bbbb:cccc:dddd")).isEqualTo("v6:20010db800010002/64");
        assertThat(ClientIps.normalize("2001:db8:1:3::1")).isEqualTo("v6:20010db800010003/64");
    }

    @Test
    @DisplayName("texto con dos puntos que no es IPv6 queda igual")
    void invalidIpv6IsUnchanged() {
        assertThat(ClientIps.normalize("no:es:una:ip:zzzz:::::")).isEqualTo("no:es:una:ip:zzzz:::::");
    }
}
