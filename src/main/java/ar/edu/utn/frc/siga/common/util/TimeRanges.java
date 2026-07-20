package ar.edu.utn.frc.siga.common.util;

import java.time.LocalTime;

/** Solapamiento de franjas horarias medio-abiertas: fin == inicio no es solapamiento. */
public final class TimeRanges {

    private TimeRanges() {
    }

    public static boolean overlaps(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        return start1.isBefore(end2) && start2.isBefore(end1);
    }
}
