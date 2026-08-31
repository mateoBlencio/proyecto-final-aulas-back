package ar.edu.utn.frc.siga.roomrequest.handler;

import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;

/**
 * Toda la lógica propia de un tipo de solicitud (validación cruzada y armado de la entidad) vive en
 * un handler. Tipo nuevo = clase nueva, sin tocar el resto (mismo criterio que las restricciones del
 * optimizer). El {@code @Valid} del controller ya corrió antes de {@link #validate}.
 */
public interface RoomRequestTypeHandler {

    RoomRequestType type();

    void validate(CreateRoomRequestDto dto);

    RoomRequest assemble(CreateRoomRequestDto dto);
}
