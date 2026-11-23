package ar.edu.utn.frc.siga.common.realtime;

/** Qué parte de los datos cambió. El front lo traduce a las consultas que tiene que volver a pedir. */
public enum LiveTopic {
    ALLOCATIONS,
    ROOM_REQUESTS,
    BUILDING_ACTIVITY
}
