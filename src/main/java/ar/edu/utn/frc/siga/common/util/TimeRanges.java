package ar.edu.utn.frc.siga.common.util;

import java.time.Duration;
import java.time.LocalTime;

public final class TimeRanges {

    private TimeRanges() {
    }

    public static boolean overlaps(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        return start1.isBefore(end2) && start2.isBefore(end1);
    }

    public static long overlapMinutes(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        LocalTime from = start1.isAfter(start2) ? start1 : start2;
        LocalTime to = end1.isBefore(end2) ? end1 : end2;
        return from.isBefore(to) ? Duration.between(from, to).toMinutes() : 0L;
    }
}
