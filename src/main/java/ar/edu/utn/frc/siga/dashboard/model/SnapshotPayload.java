package ar.edu.utn.frc.siga.dashboard.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Content of {@code tablero_snapshot.datos}. Absolute values only: percentages are derived when reading,
 * so buildings can be added up for a restricted user.
 */
public record SnapshotPayload(
        int schemaVersion, int rulesVersion, YearMonth month, LocalDate from, LocalDate to,
        SnapshotParameters parameters,
        Long buildingId, Integer buildingCode, String buildingName,
        int roomCount, int availableDays,
        CategoryCounts categories,
        Long unallocatedClasses,
        long occupiedRoomMinutes, long availableRoomMinutes,
        List<SlotMinutes> hourlyProfile,
        List<SourceCounts> bySource,
        RoomRequestCounts roomRequests,
        RoomRequestCounts unassignedBuildingRoomRequests) {

    public record SnapshotParameters(int underusedThresholdPercent, int maxOverlapMinutes,
                                     LocalTime hoursStart, LocalTime hoursEnd) {
    }

    public record SlotMinutes(int hour, long occupiedRoomMinutes, long availableRoomMinutes) {
    }

    public record SourceCounts(String source, CategoryCounts categories) {
    }

    /** Resolved and expired in the month; pending at the month close. */
    public record RoomRequestCounts(long resolved, long expired, long pending) {
    }
}
