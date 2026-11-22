package ar.edu.utn.frc.siga.events.event;

import org.springframework.modulith.NamedInterface;

import java.time.LocalDate;
import java.util.List;

/**
 * Se publica en la misma transacción de la edición de un evento único cuando cambia fecha, hora o
 * duración. Los listeners sincrónicos revalidan el aula y sincronizan pedidos; si uno lanza, la
 * edición hace rollback.
 */
@NamedInterface("api")
public record UniqueEventRescheduled(Long eventId, List<Long> occurrenceIds, LocalDate date, String observation) {
}
