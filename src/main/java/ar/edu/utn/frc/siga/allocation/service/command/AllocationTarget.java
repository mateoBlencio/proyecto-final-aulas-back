package ar.edu.utn.frc.siga.allocation.service.command;

import java.time.LocalDate;
import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public sealed interface AllocationTarget {

    @NamedInterface("api")
    record Occurrences(List<Long> occurrenceIds) implements AllocationTarget {}

    @NamedInterface("api")
    record Event(Long eventId) implements AllocationTarget {}

    /**
     * Las ocurrencias de un evento entre dos fechas, ambas inclusive.
     *
     * <p>Cubre los dos movimientos que el negocio distingue: <b>temporal</b> (se dicta en otra
     * aula entre dos fechas y después vuelve sola, porque las ocurrencias de afuera del rango
     * nunca se tocaron) y <b>permanente</b> ({@code to == null}: desde {@code from} hasta que
     * termine el dictado).
     *
     * <p>A diferencia de {@link Event}, este target <b>ignora el clamp</b> que le pasa
     * {@code AllocationServiceImpl}: trae sus propias fechas y son ellas las que mandan. Por eso
     * tampoco lleva {@code @NamedInterface}: solo lo construye el mapper de este módulo, mientras
     * que {@link Event} y {@link Occurrences} sí los instancian {@code ingest} y {@code preview}.
     */
    record EventRange(Long eventId, LocalDate from, LocalDate to) implements AllocationTarget {}
}
