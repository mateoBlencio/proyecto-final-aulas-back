package ar.edu.utn.frc.siga.sysacad.internal;

import java.util.Map;

public record ViewQuery(
        Map<String, String> filters,
        String sort,
        String direction,
        Integer limit
) {

    private static final String ASCENDING = "asc";

    public static ViewQuery ascendingBy(String sort, Integer limit) {
        return new ViewQuery(Map.of(), sort, ASCENDING, limit);
    }
}
