package ar.edu.utn.frc.siga.dashboard.service;

import java.time.YearMonth;

public interface DashboardSnapshotService {

    /**
     * Generates the closed months that have no current campus snapshot, from the first existing snapshot
     * (or only the last closed month when there is none). A failing month is logged and does not stop the rest.
     */
    void generateMissingMonths();

    /** Computes and stores the rows of one month: one per active building plus the campus. */
    void generate(YearMonth month, String actor, String reason);
}
