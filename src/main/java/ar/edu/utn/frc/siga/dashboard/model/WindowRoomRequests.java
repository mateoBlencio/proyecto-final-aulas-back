package ar.edu.utn.frc.siga.dashboard.model;

import ar.edu.utn.frc.siga.dashboard.model.SnapshotPayload.RoomRequestCounts;
import java.util.Map;

/** Room request counts of a date window: per building, for the whole campus and for items without building. */
public record WindowRoomRequests(Map<Long, RoomRequestCounts> byBuilding, RoomRequestCounts campus,
                                 RoomRequestCounts unassignedBuilding) {

    public RoomRequestCounts forBuilding(Long buildingId) {
        return byBuilding.getOrDefault(buildingId, new RoomRequestCounts(0, 0, 0));
    }
}
