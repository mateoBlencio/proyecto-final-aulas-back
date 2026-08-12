package ar.edu.utn.frc.siga.common.util;

import java.time.LocalTime;

/** Franja horaria de algo que puede chocar contra otra. Ver {@link Clashes}. */
public interface TimeSpan {
    LocalTime startTime();

    LocalTime endTime();
}
