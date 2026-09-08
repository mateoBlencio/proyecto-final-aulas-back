package ar.edu.utn.frc.siga.events.model;

import java.time.LocalDate;

/**
 * Ventana efectiva de generación de ocurrencias de un evento recurrente: el rango {@code [start, end]}
 * al que el período académico recorta las fechas del evento (clamp), y opcionalmente el hueco
 * {@code [recessStart, recessEnd]} del receso invernal a saltar (sólo para eventos anuales). Los
 * bordes nulos se interpretan como "sin límite" por ese lado.
 */
public record OccurrenceWindow(LocalDate start, LocalDate end, LocalDate recessStart, LocalDate recessEnd) {

    public static OccurrenceWindow unbounded(LocalDate fallbackStart, LocalDate fallbackEnd) {
        return new OccurrenceWindow(fallbackStart, fallbackEnd, null, null);
    }

    public LocalDate clampStart(LocalDate eventStart) {
        return start != null && start.isAfter(eventStart) ? start : eventStart;
    }

    public LocalDate clampEnd(LocalDate eventEnd) {
        return end != null && end.isBefore(eventEnd) ? end : eventEnd;
    }

    public boolean isInRecess(LocalDate date) {
        return recessStart != null && recessEnd != null
                && !date.isBefore(recessStart) && !date.isAfter(recessEnd);
    }
}
