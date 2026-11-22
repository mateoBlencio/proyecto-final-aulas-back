package ar.edu.utn.frc.siga.common.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LikePatterns")
class LikePatternsTest {

    @Test
    @DisplayName("containing escapa cada barra, porcentaje y guion bajo y envuelve en porcentajes")
    void containingEscapesWildcardsAndWraps() {
        assertThat(LikePatterns.containing("50%_a\\b")).isEqualTo("%50\\%\\_a\\\\b%");
    }

    @Test
    @DisplayName("containing de un texto sin comodines solo lo envuelve en porcentajes")
    void containingWrapsPlainText() {
        assertThat(LikePatterns.containing("abc")).isEqualTo("%abc%");
    }

    @Test
    @DisplayName("escape antepone una barra a cada comodin y duplica las barras sin envolver")
    void escapeDoesNotWrap() {
        assertThat(LikePatterns.escape("50%_a\\b")).isEqualTo("50\\%\\_a\\\\b");
    }

    @Test
    @DisplayName("escape duplica la barra una sola vez y no vuelve a escapar la barra que agrega")
    void escapeDoesNotDoubleEscape() {
        assertThat(LikePatterns.escape("\\%")).isEqualTo("\\\\\\%");
    }

    @Test
    @DisplayName("containing de un texto vacio devuelve solo los dos porcentajes")
    void containingOfEmptyText() {
        assertThat(LikePatterns.containing("")).isEqualTo("%%");
    }
}
