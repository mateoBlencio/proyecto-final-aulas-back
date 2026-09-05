package ar.edu.utn.frc.siga.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Lazy")
class LazyTest {

    @Test
    @DisplayName("get: el delegate se invoca una sola vez aunque se llame varias veces")
    void delegateSeInvocaUnaSolaVez() {
        AtomicInteger calls = new AtomicInteger();
        Lazy<String> lazy = Lazy.of(() -> {
            calls.incrementAndGet();
            return "valor";
        });

        lazy.get();
        lazy.get();
        lazy.get();

        assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("get: devuelve siempre el mismo valor resuelto")
    void devuelveSiempreElMismoValor() {
        Lazy<Object> lazy = Lazy.of(Object::new);

        Object first = lazy.get();
        Object second = lazy.get();

        assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("get: si el delegate falla la excepción se propaga y no se memoiza el fallo")
    void noMemoizaElFallo() {
        AtomicInteger calls = new AtomicInteger();
        Lazy<String> lazy = Lazy.of(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("fallo transitorio");
            }
            return "recuperado";
        });

        assertThatThrownBy(lazy::get)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fallo transitorio");

        assertThat(lazy.get()).isEqualTo("recuperado");
        assertThat(calls).hasValue(2);
    }
}
