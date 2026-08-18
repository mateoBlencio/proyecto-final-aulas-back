package ar.edu.utn.frc.siga.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Paging")
class PagingTest {

    @Test
    @DisplayName("of: página completa dentro de rango")
    void paginaCompletaDentroDeRango() {
        List<Integer> items = List.of(1, 2, 3, 4, 5);

        Page<Integer> page = Paging.of(items, PageRequest.of(0, 2));

        assertThat(page.getContent()).containsExactly(1, 2);
        assertThat(page.getTotalElements()).isEqualTo(5);
    }

    @Test
    @DisplayName("of: última página parcial devuelve solo lo que queda")
    void ultimaPaginaParcial() {
        List<Integer> items = List.of(1, 2, 3, 4, 5);

        Page<Integer> page = Paging.of(items, PageRequest.of(2, 2));

        assertThat(page.getContent()).containsExactly(5);
    }

    @Test
    @DisplayName("of: página fuera de rango devuelve contenido vacío, no lanza")
    void paginaFueraDeRangoDevuelveVacio() {
        List<Integer> items = List.of(1, 2, 3);

        Page<Integer> page = Paging.of(items, PageRequest.of(5, 2));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("of: Pageable.unpaged() no tiene tamaño de página fijo, por lo que Paging.of no lo soporta")
    void unpagedNoTienePageSize() {
        assertThatThrownBy(() -> Paging.of(List.of(1, 2, 3), Pageable.unpaged()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("of: lista vacía devuelve página vacía")
    void listaVaciaDevuelvePaginaVacia() {
        Page<Integer> page = Paging.of(List.of(), PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }
}
