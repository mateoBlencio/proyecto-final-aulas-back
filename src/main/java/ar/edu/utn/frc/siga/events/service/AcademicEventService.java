package ar.edu.utn.frc.siga.events.service;

import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.UpdateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;

import java.util.Collection;
import java.util.List;

import org.springframework.modulith.NamedInterface;

/** Fachada pública de eventos académicos (recurrentes y únicos) y sus occurrences: alta y consulta. */
@NamedInterface("api")
public interface AcademicEventService {
    List<AcademicEventResponseDto> findAll();
    AcademicEventResponseDto findById(Long eventId);

    /** Como {@link #findById}, en lote: ignora IDs inexistentes en vez de lanzar 404. */
    List<AcademicEventResponseDto> findByIds(Collection<Long> eventIds);
    List<OccurrenceResponseDto> findOccurrencesByEventId(Long eventId);

    /** Crea un evento recurrente y genera de una vez todas sus occurrences (en SCHEDULED, sin aula). */
    AcademicEventResponseDto createRecurringEvent(CreateRecurringEventRequestDto dto);

    /**
     * Reutiliza un evento recurrente idéntico (misma materia/comisión/día/horario/ventana
     * de fechas) si ya existe; si no, lo crea. Pensado para importaciones donde varias
     * filas de la planilla describen el mismo evento.
     */
    FindOrCreateResult<Long> findOrCreateRecurringEvent(CreateRecurringEventRequestDto dto);

    /**
     * Crea un evento único y genera su única occurrence, sin aula (queda NEEDS_ROOM). Asignarle
     * un aula es una llamada aparte a {@code allocation}.
     */
    AcademicEventResponseDto createUniqueEvent(CreateUniqueEventRequestDto dto);

    /** Lista todos los eventos únicos (parciales, TPs, mesas especiales, etc.). */
    List<AcademicEventResponseDto> findUniqueEvents();

    /**
     * Modifica un evento único existente (fecha, horario, alumnos, descripción), sin tocar su
     * aula. Rechaza si ya ocurrió, o si {@code id} no corresponde a un evento único (404).
     */
    AcademicEventResponseDto updateUniqueEvent(Long id, UpdateUniqueEventRequestDto dto);
}
