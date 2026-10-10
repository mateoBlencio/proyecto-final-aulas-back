package ar.edu.utn.frc.siga.dashboard.model;

/**
 * Room request item count. {@code campus} marks the row that counts each item once for the whole campus;
 * otherwise the row is for {@code buildingId} (null: no building).
 */
public record RequestCountRow(int year, Long buildingId, boolean campus, long total) {
}
