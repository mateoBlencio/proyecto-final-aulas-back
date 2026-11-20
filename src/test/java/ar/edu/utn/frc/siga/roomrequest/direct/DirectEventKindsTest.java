package ar.edu.utn.frc.siga.roomrequest.direct;

import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DirectEventKinds")
class DirectEventKindsTest {

    @ParameterizedTest
    @CsvSource({
            "ONE_TIME_ROOM_CHANGE,El tipo Cambio de aula único no crea un evento único.",
            "REGULAR_ROOM_CHANGE,El tipo Cambio de aula regular no crea un evento único."})
    @DisplayName("of: los cambios de aula lanzan un mensaje con el nombre del tipo en español, sin su código")
    void roomChangeTypesMessageUsesTheLabel(RoomRequestType type, String expected) {
        assertThatThrownBy(() -> DirectEventKinds.of(type))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expected);
    }

    @Test
    @DisplayName("of: los tipos con evento único devuelven su clase de evento")
    void supportedTypesMapToKinds() {
        assertThat(DirectEventKinds.of(RoomRequestType.CONFERENCE)).isEqualTo(UniqueEventKind.CONFERENCIA);
        assertThat(DirectEventKinds.of(RoomRequestType.OTHER)).isEqualTo(UniqueEventKind.OTRO);
    }
}
