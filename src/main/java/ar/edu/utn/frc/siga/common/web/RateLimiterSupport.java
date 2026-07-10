package ar.edu.utn.frc.siga.common.web;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

/**
 * Resuelve (o crea) el {@link Bucket} de una clave dada dentro de una caché Caffeine acotada,
 * evitando la fuga de memoria de un mapa sin eviction keyed por un valor controlado por el cliente
 * (IP, email).
 */
public final class RateLimiterSupport {

    private RateLimiterSupport() {
    }

    public static Bucket resolveBucket(String key, Bandwidth bandwidth, Cache<String, Bucket> cache) {
        return cache.get(key, k -> Bucket.builder()
                .addLimit(bandwidth)
                .build());
    }
}
