package ar.edu.utn.frc.siga.dashboard.service.impl;

import ar.edu.utn.frc.siga.dashboard.model.RequestCountRow;
import ar.edu.utn.frc.siga.dashboard.model.SnapshotPayload.RoomRequestCounts;
import ar.edu.utn.frc.siga.dashboard.model.WindowRoomRequests;
import ar.edu.utn.frc.siga.dashboard.repository.DashboardReadRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Counts room request items of a window: resolved and expired inside it, pending at the end of its last day. */
@Component
@RequiredArgsConstructor
public class DashboardRoomRequestCounter {

    private final DashboardReadRepository readRepository;
    private final Clock clock;

    public WindowRoomRequests count(LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(clock.getZone()).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        List<RequestCountRow> resolved = readRepository.countResolved(start, end);
        List<RequestCountRow> expired = readRepository.countExpired(start, end);
        List<RequestCountRow> pending = readRepository.countPendingAt(end);

        Map<Long, RoomRequestCounts> byBuilding = new HashMap<>();
        for (List<RequestCountRow> rows : List.of(resolved, expired, pending)) {
            for (RequestCountRow row : rows) {
                if (!row.campus() && row.buildingId() != null) {
                    byBuilding.computeIfAbsent(row.buildingId(), id -> new RoomRequestCounts(
                            sum(resolved, id, false), sum(expired, id, false), sum(pending, id, false)));
                }
            }
        }
        RoomRequestCounts campus = new RoomRequestCounts(
                resolved.stream().filter(RequestCountRow::campus).mapToLong(RequestCountRow::total).sum(),
                expired.stream().mapToLong(RequestCountRow::total).sum(),
                pending.stream().mapToLong(RequestCountRow::total).sum());
        RoomRequestCounts unassigned = new RoomRequestCounts(0, sum(expired, null, true), sum(pending, null, true));
        return new WindowRoomRequests(byBuilding, campus, unassigned);
    }

    /** Total of the rows of one building ({@code noBuilding}: the rows without building); campus rows are skipped. */
    private static long sum(List<RequestCountRow> rows, Long buildingId, boolean noBuilding) {
        return rows.stream().filter(row -> !row.campus())
                .filter(row -> noBuilding ? row.buildingId() == null : buildingId.equals(row.buildingId()))
                .mapToLong(RequestCountRow::total).sum();
    }
}
