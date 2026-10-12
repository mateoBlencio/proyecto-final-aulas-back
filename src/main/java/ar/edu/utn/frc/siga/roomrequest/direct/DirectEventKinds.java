package ar.edu.utn.frc.siga.roomrequest.direct;

import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;

public final class DirectEventKinds {

    private DirectEventKinds() {
    }

    public static UniqueEventKind of(RoomRequestType type) {
        return switch (type) {
            case PARTIAL_EXAM_IN_CLASS, PARTIAL_EXAM_OFF_SCHEDULE -> UniqueEventKind.PARCIAL;
            case FINAL_EXAM -> UniqueEventKind.EXAMEN_FINAL;
            case CONFERENCE -> UniqueEventKind.CONFERENCIA;
            case OTHER -> UniqueEventKind.OTRO;
            case ONE_TIME_ROOM_CHANGE, REGULAR_ROOM_CHANGE ->
                    throw new IllegalArgumentException("El tipo " + type + " no crea un evento único.");
        };
    }
}
