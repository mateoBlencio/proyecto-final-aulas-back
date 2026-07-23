package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceResponseDto;

import java.time.LocalDate;
import java.util.List;

import org.springframework.modulith.NamedInterface;

/**
 * Fachada pública de eventos académicos (recurrentes y únicos) y sus occurrences: alta,
 * consulta y el listado de eventos pendientes de asignación de aula.
 */
@NamedInterface("api")
public interface AcademicEventService {
    List<AcademicEventResponseDto> findAll();
    AcademicEventResponseDto findById(Long eventId);
    List<OccurrenceResponseDto> findOccurrencesByEventId(Long eventId);

    /** Crea un evento recurrente y genera de una vez todas sus occurrences (en SCHEDULED, sin aula). */
    AcademicEventResponseDto createRecurringEvent(CreateRecurringEventRequestDto dto);

    /**
     * Busca un evento recurrente idéntico (misma materia/comisión/día/horario/ventana de
     * fechas): catálogo cargado por fuera de esta app, no se crea desde acá. Devuelve solo
     * el id (el único caller productivo solo necesita el id).
     */
    Long findRecurringEvent(CreateRecurringEventRequestDto dto);

    /** Crea un evento único y genera su única occurrence (en SCHEDULED, sin aula). */
    AcademicEventResponseDto createUniqueEvent(CreateUniqueEventRequestDto dto);

    /**
     * Lista, agrupados por evento, los eventos con occurrences en SCHEDULED entre
     * {@code from} (default hoy) y {@code to} (sin límite superior si es null). Excluye
     * occurrences ASSIGNED/CANCELLED/SUSPENDED, y las ya pasadas salvo que
     * {@code includePast} sea true.
     */
    List<AcademicEventResponseDto> findUnassignedEvents(LocalDate from, LocalDate to, boolean includePast);
}
