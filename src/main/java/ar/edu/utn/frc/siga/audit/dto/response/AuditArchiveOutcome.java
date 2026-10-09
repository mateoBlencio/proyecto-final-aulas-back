package ar.edu.utn.frc.siga.audit.dto.response;

public enum AuditArchiveOutcome {
    NO_PERIODS,
    INVALID_PERIODS,
    NOTHING_TO_ARCHIVE,
    COMPLETED,
    STOPPED_BY_CONCURRENT_RUN
}
