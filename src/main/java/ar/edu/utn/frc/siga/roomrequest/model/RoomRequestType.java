package ar.edu.utn.frc.siga.roomrequest.model;

/**
 * Tipos de solicitudes de aulas.
 */
public enum RoomRequestType {

    PARTIAL_EXAM(true, true),
    FINAL_EXAM(true, true),
    CONFERENCE(false, true),

    /** Horario e inscriptos salen de la comisión/ocurrencia; no se piden de nuevo. */
    ONE_TIME_ROOM_CHANGE(true, false),

    /** Igual que {@link #ONE_TIME_ROOM_CHANGE}. */
    REGULAR_ROOM_CHANGE(true, false),

    /** Catch-all sin reglas propias todavía; sólo exige {@code observations}. */
    OTHER(false, true);

    private final boolean academicReferenceRequired;
    private final boolean scheduleAndEnrollmentRequired;

    RoomRequestType(boolean academicReferenceRequired, boolean scheduleAndEnrollmentRequired) {
        this.academicReferenceRequired = academicReferenceRequired;
        this.scheduleAndEnrollmentRequired = scheduleAndEnrollmentRequired;
    }

    /** Si el tipo exige materia y comisión. */
    public boolean requiresAcademicReference() {
        return academicReferenceRequired;
    }

    /** Si el pedido es para un parcial o un final. */
    public boolean isExam() {
        return this == PARTIAL_EXAM || this == FINAL_EXAM;
    }

    /** Si exige {@code startTime}/{@code endTime}/{@code enrolled}. Falso solo en cambios de aula. */
    public boolean requiresScheduleAndEnrollment() {
        return scheduleAndEnrollmentRequired;
    }
}
