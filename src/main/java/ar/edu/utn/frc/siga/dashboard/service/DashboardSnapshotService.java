package ar.edu.utn.frc.siga.dashboard.service;

import ar.edu.utn.frc.siga.dashboard.dto.response.DashboardReportResponseDto;
import java.time.YearMonth;

public interface DashboardSnapshotService {

    /**
     * Generates the closed months that have no current campus snapshot, from the first existing snapshot
     * (or only the last closed month when there is none). A failing month is logged and does not stop the rest.
     */
    void generateMissingMonths();

    /** Computes and stores the rows of one month: one per active building plus the campus. */
    void generate(YearMonth month, String actor, String reason);

    /**
     * Retires the current rows of a closed month and generates new ones; the previous version stays stored.
     * 422 if the month is not closed, 404 if it has no snapshot. Audited.
     */
    DashboardReportResponseDto regenerate(YearMonth month, String reason, String actor);
}
