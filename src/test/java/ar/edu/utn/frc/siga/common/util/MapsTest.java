package ar.edu.utn.frc.siga.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Maps")
class MapsTest {

    private record Item(Long id, String name) {
    }

    @Test
    @DisplayName("byId: indexa por la clave extraída")
    void indexaPorClave() {
        List<Item> items = List.of(new Item(1L, "a"), new Item(2L, "b"));

        Map<Long, Item> byId = Maps.byId(items, Item::id);

        assertThat(byId).containsOnlyKeys(1L, 2L);
        assertThat(byId.get(1L).name()).isEqualTo("a");
    }

    @Test
    @DisplayName("byId: lista vacía devuelve mapa vacío")
    void listaVaciaDevuelveMapaVacio() {
        assertThat(Maps.byId(List.of(), Item::id)).isEmpty();
    }

    @Test
    @DisplayName("byId: clave duplicada lanza IllegalStateException (comportamiento de toMap)")
    void claveDuplicadaLanza() {
        List<Item> items = List.of(new Item(1L, "a"), new Item(1L, "b"));

        assertThatThrownBy(() -> Maps.byId(items, Item::id)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("byId con merge: clave duplicada se resuelve con el operador en vez de lanzar")
    void claveDuplicadaSeResuelveConMerge() {
        List<Item> items = List.of(new Item(1L, "a"), new Item(1L, "b"), new Item(2L, "c"));

        Map<Long, Item> byId = Maps.byId(items, Item::id, (first, second) -> second);

        assertThat(byId).containsOnlyKeys(1L, 2L);
        assertThat(byId.get(1L).name()).isEqualTo("b");
    }
}
