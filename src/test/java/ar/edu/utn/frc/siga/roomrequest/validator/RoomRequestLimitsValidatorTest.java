package ar.edu.utn.frc.siga.roomrequest.validator;

import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateConferenceDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.FreeFormItemDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.RequesterInfo;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.settings.api.SettingsReader;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestLimitsValidator")
class RoomRequestLimitsValidatorTest {

    @Mock
    private SettingsReader settingsReader;

    private RoomRequestLimitsValidator validator;

    @BeforeEach
    void setUp() {
        when(settingsReader.getInt(SettingKey.ROOM_REQUESTS_MAX_CLASSROOM_COUNT)).thenReturn(20);
        when(settingsReader.getInt(SettingKey.ROOM_REQUESTS_MAX_ESTIMATED)).thenReturn(500);
        when(settingsReader.getInt(SettingKey.ROOM_REQUESTS_MAX_COMPUTER_COUNT)).thenReturn(500);
        validator = new RoomRequestLimitsValidator(settingsReader);
    }

    private static CreateConferenceDto dto(Integer classrooms, Integer estimated, Boolean pcs, Integer pcCount) {
        FreeFormItemDto item = new FreeFormItemDto(null, LocalDate.now().plusDays(7), LocalTime.of(10, 0),
                LocalTime.of(12, 0), estimated, classrooms, false, pcs, pcCount, null, null, null, List.of());
        return new CreateConferenceDto(RoomRequestType.CONFERENCE,
                new RequesterInfo(AcademicScope.GRADO, "Ada", "ada@frc.utn.edu.ar", "351"), null, List.of(item));
    }

    @Test
    @DisplayName("en el tope exacto pasa: 20 aulas, 500 asistentes y 500 computadoras")
    void atTheLimit() {
        assertThatCode(() -> validator.validate(dto(20, 500, true, 500))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("21 aulas: 400")
    void tooManyClassrooms() {
        assertThatThrownBy(() -> validator.validate(dto(21, 10, false, null)))
                .isInstanceOf(InvalidRoomRequestException.class).hasMessageContaining("aulas");
    }

    @Test
    @DisplayName("1200 asistentes: 400")
    void tooManyAttendees() {
        assertThatThrownBy(() -> validator.validate(dto(1, 1200, false, null)))
                .isInstanceOf(InvalidRoomRequestException.class).hasMessageContaining("asistentes");
    }

    @Test
    @DisplayName("501 computadoras: 400")
    void tooManyComputers() {
        assertThatThrownBy(() -> validator.validate(dto(1, 10, true, 501)))
                .isInstanceOf(InvalidRoomRequestException.class).hasMessageContaining("computadoras");
    }

    @Test
    @DisplayName("sin aulas, asistentes ni computadoras informados: pasa")
    void nullsAreIgnored() {
        assertThatCode(() -> validator.validate(dto(null, 0, false, null))).doesNotThrowAnyException();
    }
}
