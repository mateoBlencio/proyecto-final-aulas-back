package ar.edu.utn.frc.siga.realtime.internal;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.common.realtime.LiveTopic;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LiveDelivery: quién recibe cada aviso")
class LiveDeliveryTest {

    private static final BuildingScope BUILDING_1 = BuildingScope.of(Set.of(1L));

    @Test
    @DisplayName("Un cambio en un edificio llega a quien ve ese edificio y no a quien ve otro")
    void scopedChangeReachesOnlyMatchingBuildings() {
        LiveDelivery delivery = new LiveDelivery(LiveTopic.ALLOCATIONS, Set.of(1L), false);

        assertThat(delivery.reaches(BUILDING_1)).isTrue();
        assertThat(delivery.reaches(BuildingScope.of(Set.of(2L)))).isFalse();
        assertThat(delivery.reaches(BuildingScope.unrestricted())).isTrue();
    }

    @Test
    @DisplayName("Un cambio sin edificio llega a todo el que vea el tema")
    void unscopedChangeReachesEveryReader() {
        LiveDelivery delivery = new LiveDelivery(LiveTopic.ALLOCATIONS, Set.of(), true);

        assertThat(delivery.reaches(BUILDING_1)).isTrue();
        assertThat(delivery.reaches(BuildingScope.unrestricted())).isTrue();
    }

    @Test
    @DisplayName("Quien no ve el tema no recibe nada, ni siquiera un cambio sin edificio")
    void deniedScopeReceivesNothing() {
        assertThat(new LiveDelivery(LiveTopic.ALLOCATIONS, Set.of(), true).reaches(BuildingScope.denied())).isFalse();
        assertThat(new LiveDelivery(LiveTopic.ALLOCATIONS, Set.of(1L), false).reaches(BuildingScope.denied())).isFalse();
        assertThat(new LiveDelivery(LiveTopic.ALLOCATIONS, Set.of(1L), false).reaches(null)).isFalse();
    }

    @Test
    @DisplayName("Un cambio sin edificios ni marca general solo llega a quien ve todo")
    void emptyScopedChangeReachesOnlyUnrestricted() {
        LiveDelivery delivery = new LiveDelivery(LiveTopic.ROOM_REQUESTS, Set.of(), false);

        assertThat(delivery.reaches(BUILDING_1)).isFalse();
        assertThat(delivery.reaches(BuildingScope.unrestricted())).isTrue();
    }
}
