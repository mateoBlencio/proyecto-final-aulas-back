package ar.edu.utn.frc.siga.ingest.service.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

class IngestCache {

    private final Map<String, Object> values = new HashMap<>();

    @SuppressWarnings("unchecked")
    <T> T get(Class<T> type, Object key, Supplier<T> loader) {
        return (T) values.computeIfAbsent(type.getSimpleName() + ":" + key, k -> loader.get());
    }
}
