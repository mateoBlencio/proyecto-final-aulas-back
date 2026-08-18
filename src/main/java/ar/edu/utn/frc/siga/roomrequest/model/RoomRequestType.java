package ar.edu.utn.frc.siga.roomrequest.model;

/**
 * Tipos de solicitudes de aulas.
 */
public enum RoomRequestType {

    /** Solicitud de aulas para parcial. */
    PARTIAL_EXAM(true, true),

    /** Solicitud de aulas para final. */
    FINAL_EXAM(true, true),

    /** Solicitud de charla, conferencia o curso. */
    CONFERENCE(false, true),

    /**
     * Cambio de aula para dictado de clases por única vez. La comisión ya
     * define horario e inscriptos por ocurrencia, así que el formulario no
     * necesita pedirlos de nuevo.
     */
    ONE_TIME_ROOM_CHANGE(true, false),

    /**
     * Cambio de aulas para dictado regular de clases. Mismo motivo que
     * {@link #ONE_TIME_ROOM_CHANGE}: horario e inscriptos salen de la comisión.
     */
    REGULAR_ROOM_CHANGE(true, false),

    /**
     * Catch-all para pedidos que no encajan en ningún otro tipo. Sin reglas de
     * negocio propias todavía: se creó para no bloquear casos que no
     * anticipamos, así que a propósito es el tipo con menos restricciones.
     * Lo único que exige {@code RoomRequestValidator} es que cada pedido
     * traiga {@code observations} — es la única forma de que subsecretaría
     * sepa de qué se trata sin agregar un campo nuevo al schema.
     */
    OTHER(false, true);

    private final boolean academicReferenceRequired;
    private final boolean scheduleAndEnrollmentRequired;

    RoomRequestType(boolean academicReferenceRequired, boolean scheduleAndEnrollmentRequired) {
        this.academicReferenceRequired = academicReferenceRequired;
        this.scheduleAndEnrollmentRequired = scheduleAndEnrollmentRequired;
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

    /**
     * Si cada pedido de este tipo debe traer {@code startTime}/{@code endTime}/
     * {@code enrolled}. Falso solo en los cambios de aula: ahí esos datos ya
     * existen por comisión/ocurrencia y pedirlos de nuevo sería redundante.
     */
    public boolean requiresScheduleAndEnrollment() {
        return scheduleAndEnrollmentRequired;
    }
}
