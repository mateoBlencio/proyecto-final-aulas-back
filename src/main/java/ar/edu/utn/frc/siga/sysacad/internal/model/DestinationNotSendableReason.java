package ar.edu.utn.frc.siga.sysacad.internal.model;

public enum DestinationNotSendableReason {
    BUILDING_NOT_IN_SYSACAD,
    CLASSROOM_NOT_IN_SYSACAD,
    CLASSROOM_DISABLED_IN_SYSACAD,
    CLASSROOM_WITHOUT_NUMBER,
    CLASSROOM_RESERVED_NUMBER,
    BUILDING_WITHOUT_CODE,
    CLASSROOM_NOT_FOUND
}
