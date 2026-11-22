package ar.edu.utn.frc.siga.audit.repository;

import java.util.List;

/** Operations of a causal chain; {@code truncated} is true if the operation or depth cap cut it. */
public record AuditChainRows(List<AuditGroupRow> rows, boolean truncated) {
}
