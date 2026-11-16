package ar.edu.utn.frc.siga.events.dto;

import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OccurrenceSlotDto.blocksAllocation")
class OccurrenceSlotDtoTest {

    private static OccurrenceSlotDto slot(OccurrenceStatus status, ReleaseReason reason) {
        return new OccurrenceSlotDto(1L, 1L, LocalDate.now().plusDays(3), LocalTime.of(8, 0), LocalTime.of(10, 0),
                status, 30, 1, reason, null, null, null);
    }

    @Test
    @DisplayName("una ocurrencia NEEDS_ROOM no bloquea")
    void needsRoomNoBloquea() {
        assertThat(slot(OccurrenceStatus.NEEDS_ROOM, null).blocksAllocation()).isFalse();
    }

    @Test
    @DisplayName("ROOM_RELEASED por CLASSROOM_DEACTIVATED no bloquea")
    void liberadaPorBajaNoBloquea() {
        assertThat(slot(OccurrenceStatus.ROOM_RELEASED, ReleaseReason.CLASSROOM_DEACTIVATED).blocksAllocation())
                .isFalse();
    }

    @ParameterizedTest(name = "ROOM_RELEASED por {0} bloquea")
    @EnumSource(value = ReleaseReason.class, names = "CLASSROOM_DEACTIVATED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("ROOM_RELEASED con cualquier otro motivo bloquea")
    void liberadaPorOtroMotivoBloquea(ReleaseReason reason) {
        assertThat(slot(OccurrenceStatus.ROOM_RELEASED, reason).blocksAllocation()).isTrue();
    }

    @Test
    @DisplayName("ROOM_RELEASED sin motivo bloquea")
    void liberadaSinMotivoBloquea() {
        assertThat(slot(OccurrenceStatus.ROOM_RELEASED, null).blocksAllocation()).isTrue();
    }
}
