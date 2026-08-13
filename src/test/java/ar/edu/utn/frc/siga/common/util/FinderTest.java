package ar.edu.utn.frc.siga.common.util;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Finder")
class FinderTest {

    @Test
    @DisplayName("orThrow: encontrado devuelve el valor")
    void encontradoDevuelveElValor() {
        String result = Finder.orThrow(id -> Optional.of("valor"), 1L, "Resource");

        assertThat(result).isEqualTo("valor");
    }

    @Test
    @DisplayName("orThrow: no encontrado lanza ResourceNotFoundException con el recurso y el id en el mensaje")
    void noEncontradoLanzaConMensaje() {
        assertThatThrownBy(() -> Finder.orThrow(id -> Optional.empty(), 42L, "Classroom"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Classroom")
                .hasMessageContaining("42");
    }
}
