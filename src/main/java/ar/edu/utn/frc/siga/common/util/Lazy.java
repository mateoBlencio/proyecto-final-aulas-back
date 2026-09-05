package ar.edu.utn.frc.siga.common.util;

import java.util.function.Supplier;

public final class Lazy<T> implements Supplier<T> {

    private final Supplier<T> delegate;
    private volatile boolean resolved;
    private T value;

    private Lazy(Supplier<T> delegate) {
        this.delegate = delegate;
    }

    public static <T> Lazy<T> of(Supplier<T> delegate) {
        return new Lazy<>(delegate);
    }

    @Override
    public T get() {
        if (!resolved) {
            synchronized (this) {
                if (!resolved) {
                    value = delegate.get();
                    resolved = true;
                }
            }
        }
        return value;
    }
}
