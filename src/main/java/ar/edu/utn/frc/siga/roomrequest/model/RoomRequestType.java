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
    REGULAR_ROOM_CHANGE(true),

    /**
     * Catch-all para pedidos que no encajan en ningún otro tipo. Sin reglas de
     * negocio propias todavía: se creó para no bloquear casos que no
     * anticipamos, así que a propósito es el tipo con menos restricciones.
     * Lo único que exige {@code RoomRequestValidator} es que cada pedido
     * traiga {@code observations} — es la única forma de que subsecretaría
     * sepa de qué se trata sin agregar un campo nuevo al schema.
     */
    OTHER(false);

    private final boolean academicReferenceRequired;

    RoomRequestType(boolean academicReferenceRequired) {
        this.academicReferenceRequired = academicReferenceRequired;
    }

    /**
     * Si el tipo exige materia y comisión. Una charla, conferencia u otro tipo
     * sin definir no está atada a una materia; el resto sí. Mismo criterio que
     * {@code EventScheduleValidator.validateAcademicReference} en {@code events}.
     */
    public boolean requiresAcademicReference() {
        return academicReferenceRequired;
    }

    /** Si el pedido es para un parcial o un final. */
    public boolean isExam() {
        return this == PARTIAL_EXAM || this == FINAL_EXAM;
    }
}
