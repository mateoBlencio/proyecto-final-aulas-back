package ar.edu.utn.frc.siga.roomrequest.model;

/**
 * Tipos de solicitudes de aulas.
 */
public enum RoomRequestType {

    /** Solicitud de aulas para parcial. */
    PARTIAL_EXAM(true),

    /** Solicitud de aulas para final. */
    FINAL_EXAM(true),

    /** Solicitud de charla, conferencia o curso. */
    CONFERENCE(false),

    /** Cambio de aula para dictado de clases por única vez. */
    ONE_TIME_ROOM_CHANGE(true),

    /** Cambio de aulas para dictado regular de clases. */
    REGULAR_ROOM_CHANGE(true);

    private final boolean academicReferenceRequired;

    RoomRequestType(boolean academicReferenceRequired) {
        this.academicReferenceRequired = academicReferenceRequired;
    }

    /**
     * Si el tipo exige materia y comisión. Una charla o conferencia no está atada
     * a una materia; el resto sí. Mismo criterio que
     * {@code EventScheduleValidator.validateAcademicReference} en {@code events}.
     */
    public boolean requiresAcademicReference() {
        return academicReferenceRequired;
    }

    /** Si el pedido es para reubicar una cursada existente en vez de crear algo nuevo. */
    public boolean isRoomChange() {
        return this == ONE_TIME_ROOM_CHANGE || this == REGULAR_ROOM_CHANGE;
    }
}
