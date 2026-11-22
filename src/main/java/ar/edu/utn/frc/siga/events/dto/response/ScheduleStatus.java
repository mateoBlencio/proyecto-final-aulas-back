package ar.edu.utn.frc.siga.events.dto.response;

import org.springframework.modulith.NamedInterface;

/** Estado del cursado recurrente de una materia-comisión, para explicar por qué no hay días vigentes. */
@NamedInterface("api")
public enum ScheduleStatus {
    ACTIVE,
    ENDED,
    DISABLED_IN_SYSACAD,
    NO_SCHEDULE
}
