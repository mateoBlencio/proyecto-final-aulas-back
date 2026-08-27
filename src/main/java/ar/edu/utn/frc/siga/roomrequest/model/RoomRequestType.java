package ar.edu.utn.frc.siga.roomrequest.model;

public enum RoomRequestType {

    PARTIAL_EXAM(true, true),
    FINAL_EXAM(true, true),
    CONFERENCE(false, true),

    ONE_TIME_ROOM_CHANGE(true, false),

    REGULAR_ROOM_CHANGE(true, false),

    OTHER(false, true);

    private final boolean academicReferenceRequired;
    private final boolean scheduleAndEnrollmentRequired;

    RoomRequestType(boolean academicReferenceRequired, boolean scheduleAndEnrollmentRequired) {
        this.academicReferenceRequired = academicReferenceRequired;
        this.scheduleAndEnrollmentRequired = scheduleAndEnrollmentRequired;
    }

    public boolean requiresAcademicReference() {
        return academicReferenceRequired;
    }

    public boolean isExam() {
        return this == PARTIAL_EXAM || this == FINAL_EXAM;
    }

    public boolean requiresScheduleAndEnrollment() {
        return scheduleAndEnrollmentRequired;
    }
}
