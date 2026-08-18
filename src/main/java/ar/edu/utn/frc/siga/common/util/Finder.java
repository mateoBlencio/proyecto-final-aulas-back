package ar.edu.utn.frc.siga.common.util;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.function.Function;

@Slf4j
public final class Finder {

    private Finder() {
    }

    public static <ID, T> T orThrow(Function<ID, Optional<T>> finder, ID id, String resource) {
        return finder.apply(id).orElseThrow(() -> {
            log.warn("{} no encontrado: id={}", resource, id);
            return ResourceNotFoundException.of(resource, id);
        });
    }
}