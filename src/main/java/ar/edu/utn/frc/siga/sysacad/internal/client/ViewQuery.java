package ar.edu.utn.frc.siga.sysacad.internal.client;

import java.util.Map;

public record ViewQuery(
        Map<String, String> filters,
        String sort,
        String direction,
        Integer limit
) {

    public static ViewQuery none() {
        return new ViewQuery(Map.of(), null, null, null);
    }
}
