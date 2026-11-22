package ar.edu.utn.frc.siga.roomrequest.validator;

import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestItemDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.settings.api.SettingsReader;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomRequestLimitsValidator {

    private final SettingsReader settingsReader;

    public void validate(CreateRoomRequestDto dto) {
        int maxClassrooms = settingsReader.getInt(SettingKey.ROOM_REQUESTS_MAX_CLASSROOM_COUNT);
        int maxEstimated = settingsReader.getInt(SettingKey.ROOM_REQUESTS_MAX_ESTIMATED);
        int maxComputers = settingsReader.getInt(SettingKey.ROOM_REQUESTS_MAX_COMPUTER_COUNT);
        for (CreateRoomRequestItemDto item : dto.items()) {
            requireAtMost(item.classroomCount(), maxClassrooms, "La cantidad de aulas");
            requireAtMost(item.estimated(), maxEstimated, "La cantidad de asistentes");
            requireAtMost(item.computerCount(), maxComputers, "La cantidad de computadoras");
        }
    }

    private static void requireAtMost(Integer value, int max, String what) {
        if (value != null && value > max) {
            throw new InvalidRoomRequestException(what + " no puede superar " + max + ".");
        }
    }
}
