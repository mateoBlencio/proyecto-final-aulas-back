package ar.edu.utn.frc.siga.realtime.internal;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.common.realtime.LiveChange;
import ar.edu.utn.frc.siga.common.realtime.LiveTopic;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LiveChangeBuffer: agrupa los cambios por tema")
class LiveChangeBufferTest {

    @Test
    @DisplayName("Mil cambios del mismo tema salen como un solo aviso con la unión de edificios y aulas")
    void mergesChangesOfTheSameTopic() {
        LiveChangeBuffer buffer = new LiveChangeBuffer();
        for (long i = 0; i < 1000; i++) {
            buffer.add(LiveChange.inClassrooms(LiveTopic.ALLOCATIONS, Set.of(i % 3)));
        }
        buffer.add(LiveChange.inBuildings(LiveTopic.ALLOCATIONS, Set.of(7L)));

        List<LiveChangeBuffer.Drained> drained = buffer.drain();

        assertThat(drained).singleElement().satisfies(d -> {
            assertThat(d.topic()).isEqualTo(LiveTopic.ALLOCATIONS);
            assertThat(d.classroomIds()).containsExactlyInAnyOrder(0L, 1L, 2L);
            assertThat(d.buildingIds()).containsExactly(7L);
            assertThat(d.unscoped()).isFalse();
        });
    }

    @Test
    @DisplayName("Un cambio sin edificio marca general a todo el tema, y los temas no se mezclan")
    void unscopedWinsWithinTopicOnly() {
        LiveChangeBuffer buffer = new LiveChangeBuffer();
        buffer.add(LiveChange.inBuildings(LiveTopic.ALLOCATIONS, Set.of(1L)));
        buffer.add(LiveChange.unscoped(LiveTopic.ALLOCATIONS));
        buffer.add(LiveChange.inBuildings(LiveTopic.ROOM_REQUESTS, Set.of(2L)));

        List<LiveChangeBuffer.Drained> drained = buffer.drain();

        assertThat(drained).hasSize(2);
        assertThat(drained).filteredOn(d -> d.topic() == LiveTopic.ALLOCATIONS)
                .singleElement().extracting(LiveChangeBuffer.Drained::unscoped).isEqualTo(true);
        assertThat(drained).filteredOn(d -> d.topic() == LiveTopic.ROOM_REQUESTS)
                .singleElement().extracting(LiveChangeBuffer.Drained::unscoped).isEqualTo(false);
    }

    @Test
    @DisplayName("Después de vaciarlo no repite avisos")
    void drainEmptiesTheBuffer() {
        LiveChangeBuffer buffer = new LiveChangeBuffer();
        buffer.add(LiveChange.unscoped(LiveTopic.ALLOCATIONS));
        buffer.drain();

        assertThat(buffer.drain()).isEmpty();
    }
}
