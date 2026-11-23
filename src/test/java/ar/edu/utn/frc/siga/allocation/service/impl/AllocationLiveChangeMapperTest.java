package ar.edu.utn.frc.siga.allocation.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.common.realtime.LiveChange;
import ar.edu.utn.frc.siga.common.realtime.LiveChangeMapper.PreviousState;
import ar.edu.utn.frc.siga.common.realtime.LiveTopic;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AllocationLiveChangeMapper")
class AllocationLiveChangeMapperTest {

    private final AllocationLiveChangeMapper mapper = new AllocationLiveChangeMapper();

    private static Allocation inClassroom(Long classroomId) {
        return Allocation.builder().occurrenceId(1L).classroomId(classroomId).source(AllocationSource.MANUAL).build();
    }

    @Test
    @DisplayName("Un alta avisa por el aula asignada")
    void insertUsesCurrentClassroom() {
        LiveChange change = mapper.map(inClassroom(10L), PreviousState.NONE);

        assertThat(change.topic()).isEqualTo(LiveTopic.ALLOCATIONS);
        assertThat(change.classroomIds()).containsExactly(10L);
        assertThat(change.unscoped()).isFalse();
    }

    @Test
    @DisplayName("Un cambio de aula avisa por el aula nueva y por la anterior")
    void updateIncludesPreviousClassroom() {
        LiveChange change = mapper.map(inClassroom(10L), Map.<String, Object>of("classroomId", 20L)::get);

        assertThat(change.classroomIds()).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    @DisplayName("Sin estado anterior conocido el aviso es general")
    void unknownPreviousStateIsUnscoped() {
        assertThat(mapper.map(inClassroom(10L), null).unscoped()).isTrue();
    }
}
