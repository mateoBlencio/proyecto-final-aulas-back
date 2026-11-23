package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.common.realtime.LiveChange;
import ar.edu.utn.frc.siga.common.realtime.LiveChangeMapper;
import ar.edu.utn.frc.siga.common.realtime.LiveTopic;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Un pedido afecta al edificio al que está derivado y, si dejó de estarlo (devolución o toma a cargo), al anterior.
 * Sin edificio (un NEW) solo lo reciben quienes ven todos los pedidos.
 */
@Component
class RoomRequestItemLiveChangeMapper implements LiveChangeMapper<RoomRequestItem> {

    @Override
    public Class<RoomRequestItem> entityType() {
        return RoomRequestItem.class;
    }

    @Override
    public LiveChange map(RoomRequestItem current, PreviousState previous) {
        if (previous == null) {
            return LiveChange.unscoped(LiveTopic.ROOM_REQUESTS);
        }
        Set<Long> buildingIds = new HashSet<>();
        if (current.getDerivedBuildingId() != null) buildingIds.add(current.getDerivedBuildingId());
        if (previous.get("derivedBuildingId") instanceof Long before) buildingIds.add(before);
        return LiveChange.inBuildings(LiveTopic.ROOM_REQUESTS, buildingIds);
    }
}
