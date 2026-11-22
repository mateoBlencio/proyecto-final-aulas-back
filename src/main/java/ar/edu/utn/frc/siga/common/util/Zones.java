package ar.edu.utn.frc.siga.common.util;

import java.time.ZoneId;

public final class Zones {

    /** Zona de la institución: los filtros por fecha que manda el usuario son días calendario locales. */
    public static final ZoneId LOCAL = ZoneId.of("America/Argentina/Buenos_Aires");

    private Zones() {
    }
}
