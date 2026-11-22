package ar.edu.utn.frc.siga.dashboard.model;

import java.time.LocalTime;

public record DashboardParameters(
        int underusedThresholdPercent, int maxOverlapMinutes,
        LocalTime hoursStart, LocalTime hoursEnd, int rulesVersion) {

    public int startMinute() {
        return hoursStart.toSecondOfDay() / 60;
    }

    /** Fin 00:00 equivale a fin de día (1440). */
    public int endMinute() {
        return LocalTime.MIDNIGHT.equals(hoursEnd) ? 24 * 60 : hoursEnd.toSecondOfDay() / 60;
    }

    /** First hour of the profile (inclusive). */
    public int firstSlotHour() {
        return hoursStart.getHour();
    }

    /** Last hour of the profile (inclusive): the hour containing the last minute before hoursEnd. */
    public int lastSlotHour() {
        return hoursEnd.minusMinutes(1).getHour();
    }
}
