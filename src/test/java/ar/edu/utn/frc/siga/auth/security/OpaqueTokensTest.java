package ar.edu.utn.frc.siga.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OpaqueTokens")
class OpaqueTokensTest {

    @Test
    @DisplayName("dos tokens generados difieren y miden 43 caracteres")
    void generateProducesDistinctTokensOf43Chars() {
        String first = OpaqueTokens.generate();
        String second = OpaqueTokens.generate();

        assertThat(first).isNotEqualTo(second).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(second).hasSize(43);
    }

    @Test
    @DisplayName("hash es determinista, distinto por entrada y no contiene el token crudo")
    void hashIsDeterministicAndDoesNotContainRaw() {
        String raw = OpaqueTokens.generate();

        String hash = OpaqueTokens.hash(raw);

        assertThat(hash).isEqualTo(OpaqueTokens.hash(raw)).isNotEqualTo(raw).doesNotContain(raw);
        assertThat(OpaqueTokens.hash(OpaqueTokens.generate())).isNotEqualTo(hash);
    }
}
