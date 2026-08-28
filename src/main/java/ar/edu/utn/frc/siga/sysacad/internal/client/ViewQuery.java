package ar.edu.utn.frc.siga.sysacad.internal.client;

import java.util.Map;

public record ViewQuery(
        Map<String, String> filters,
        String sort,
        String direction,
        Integer limit
) {

    private static final String ASCENDING = "asc";
    private static final String DESCENDING = "desc";

    public static ViewQuery ascendingBy(String sort, Integer limit) {
        return new ViewQuery(Map.of(), sort, ASCENDING, limit);
    }

    public static ViewQuery descendingBy(String sort, Integer limit) {
        return new ViewQuery(Map.of(), sort, DESCENDING, limit);
    }
}
