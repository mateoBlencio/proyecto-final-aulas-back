package ar.edu.utn.frc.siga.dashboard.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class DashboardRules {

    /** Version of the classification and universe rules; stored in every snapshot. */
    public static final int RULES_VERSION = 1;

    /** Version of the snapshot JSON layout. */
    public static final int SCHEMA_VERSION = 1;

    /** Fixed order for the breakdown by allocation origin. */
    public static final List<String> SOURCES = List.of("MANUAL", "AUTOMATIC", "IMPORTED", "SYSACAD");

    private DashboardRules() {
    }

    /** Percentage with one decimal (HALF_UP), or null when the total is zero. */
    public static Double percent(long part, long total) {
        if (total == 0) {
            return null;
        }
        return round1(100.0 * part / total);
    }

    public static double hours(long minutes) {
        return round1(minutes / 60.0);
    }

    public static double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
