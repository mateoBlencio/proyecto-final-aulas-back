package ar.edu.utn.frc.siga.common.util;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Maps {

    private Maps() {
    }

    public static <ID, T> Map<ID, T> byId(List<T> items, Function<T, ID> idExtractor) {
        return items.stream().collect(Collectors.toMap(idExtractor, Function.identity()));
    }
}
