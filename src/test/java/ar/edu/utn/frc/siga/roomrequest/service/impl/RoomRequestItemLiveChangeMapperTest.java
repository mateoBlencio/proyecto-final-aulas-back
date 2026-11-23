package ar.edu.utn.frc.siga.roomrequest.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.common.realtime.LiveChange;
import ar.edu.utn.frc.siga.common.realtime.LiveChangeMapper.PreviousState;
import ar.edu.utn.frc.siga.common.realtime.LiveTopic;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RoomRequestItemLiveChangeMapper")
class RoomRequestItemLiveChangeMapperTest {

    private final RoomRequestItemLiveChangeMapper mapper = new RoomRequestItemLiveChangeMapper();

    @Test
    @DisplayName("Un pedido sin derivar no lleva edificios: solo lo recibe quien ve todos los pedidos")
    void newItemHasNoBuildings() {
        LiveChange change = mapper.map(RoomRequestItem.builder().build(), PreviousState.NONE);

        assertThat(change.topic()).isEqualTo(LiveTopic.ROOM_REQUESTS);
        assertThat(change.buildingIds()).isEmpty();
        assertThat(change.unscoped()).isFalse();
    }

    @Test
    @DisplayName("Un pedido derivado avisa a ese edificio")
    void derivedItemUsesBuilding() {
        RoomRequestItem item = RoomRequestItem.builder().build();
        item.deriveTo(5L, "subsecretaria@frc.utn.edu.ar", Instant.now());

        assertThat(mapper.map(item, Map.<String, Object>of()::get).buildingIds()).containsExactly(5L);
    }

    @Test
    @DisplayName("Un pedido devuelto avisa al edificio del que salió")
    void returnedItemNotifiesPreviousBuilding() {
        RoomRequestItem item = RoomRequestItem.builder().build();
        item.deriveTo(5L, "subsecretaria@frc.utn.edu.ar", Instant.now());
        item.returnFromBuilding("sin aulas");

        LiveChange change = mapper.map(item, Map.<String, Object>of("derivedBuildingId", 5L)::get);

        assertThat(change.buildingIds()).containsExactly(5L);
    }
}
