package ar.edu.utn.frc.siga.roomrequest.validator;

import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import org.springframework.stereotype.Component;

/**
 * Sólo un pedido que necesita asignación especial (laboratorio, software, etc.) se deriva a un
 * edificio; el resto lo resuelve subsecretaría directamente. Aislado porque la definición de
 * "necesita asignación especial" puede cambiar (hoy es {@link RoomRequestItem#requiresSpecialAssignment()}).
 */
@Component
public class RoomRequestDeriveEligibilityValidator {

    public void validate(RoomRequestItem item) {
        if (!item.requiresSpecialAssignment()) {
            throw new InvalidRoomRequestException(
                    "Sólo se puede derivar un pedido que requiera asignación especial (computadoras, "
                            + "software específico o uso exclusivo para examen).");
        }
    }
}
