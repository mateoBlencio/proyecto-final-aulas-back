package ar.edu.utn.frc.siga.allocation.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.modulith.NamedInterface;

import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;

/**
 * Ocupación firme de aulas (occurrences con {@code Allocation}) en un rango de fechas.
 * Único acceso cross-módulo a esa lectura — hoy lo consume {@code preview} para pinnear
 * contra el solver la ocupación de eventos ajenos al preview que está generando.
 */
@NamedInterface("api")
public interface AllocationOccupancyService {

    /** Ocupación firme en el rango: qué aula está tomada, qué día, en qué franja. */
    List<OccupiedSlot> findOccupancy(LocalDate from, LocalDate to);
}
