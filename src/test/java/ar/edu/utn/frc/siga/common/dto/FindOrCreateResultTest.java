package ar.edu.utn.frc.siga.common.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("FindOrCreateResult")
class FindOrCreateResultTest {

    @Test
    @DisplayName("resolve: si el valor existe, no invoca al supplier y created queda en false")
    void resolveWithExistingValueDoesNotInvokeSupplier() {
        @SuppressWarnings("unchecked")
        Supplier<String> creator = mock(Supplier.class);

        FindOrCreateResult<String> result = FindOrCreateResult.resolve(Optional.of("existente"), creator);

        assertThat(result.value()).isEqualTo("existente");
        assertThat(result.created()).isFalse();
        verify(creator, never()).get();
    }

    @Test
    @DisplayName("resolve: si el valor no existe, invoca al supplier una vez y created queda en true")
    void resolveWithoutExistingValueInvokesSupplierOnce() {
        @SuppressWarnings("unchecked")
        Supplier<String> creator = mock(Supplier.class);
        when(creator.get()).thenReturn("creado");

        FindOrCreateResult<String> result = FindOrCreateResult.resolve(Optional.empty(), creator);

        assertThat(result.value()).isEqualTo("creado");
        assertThat(result.created()).isTrue();
        verify(creator, times(1)).get();
    }

    @Test
    @DisplayName("map: transforma el valor preservando el flag created (rama existente)")
    void mapPreservesCreatedFalse() {
        FindOrCreateResult<Integer> original = new FindOrCreateResult<>(42, false);

        FindOrCreateResult<String> mapped = original.map(value -> "n=" + value);

        assertThat(mapped.value()).isEqualTo("n=42");
        assertThat(mapped.created()).isFalse();
    }

    @Test
    @DisplayName("map: transforma el valor preservando el flag created (rama creada)")
    void mapPreservesCreatedTrue() {
        FindOrCreateResult<Integer> original = new FindOrCreateResult<>(7, true);

        FindOrCreateResult<String> mapped = original.map(value -> "n=" + value);

        assertThat(mapped.value()).isEqualTo("n=7");
        assertThat(mapped.created()).isTrue();
    }
}
