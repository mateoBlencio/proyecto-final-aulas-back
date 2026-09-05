package ar.edu.utn.frc.siga.events.service;

import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.UpdateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.service.command.SyncRecurringEventCommand;
import ar.edu.utn.frc.siga.events.service.command.UpsertRecurringEventResult;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface AcademicEventService {
    List<AcademicEventResponseDto> findAll();
    AcademicEventResponseDto findById(Long eventId);

    List<AcademicEventResponseDto> findByIds(Collection<Long> eventIds);
    List<OccurrenceResponseDto> findOccurrencesByEventId(Long eventId);

    List<RecurringEventResponseDto> findRecurringEventsBySubjectAndCommission(Long subjectId, Long commissionId);

    List<OccurrenceResponseDto> findClassOccurrences(Long subjectId, Long commissionId, LocalDate from);

    AcademicEventResponseDto createRecurringEvent(CreateRecurringEventRequestDto dto);

    FindOrCreateResult<Long> findOrCreateRecurringEvent(CreateRecurringEventRequestDto dto);

    /**
     * Upsert por clave natural (subjectId, commissionId, dayOfWeek, startTime, startDate, endDate)
     * desde una fuente externa (p. ej. sync de SysAcad): crea si no existe, o reconcilia
     * asimétricamente si ya existe — {@code enrolled} se pisa siempre, {@code duration} sólo si nadie
     * la tocó desde la última vez que el sync escribió (ver .claude/docs/plan-sync-eventos-sysacad.md
     * §4). No re-expande ocurrencias en el camino de actualización.
     */
    UpsertRecurringEventResult syncRecurringEvent(SyncRecurringEventCommand cmd);

    /**
     * Marca {@code sysacadEnabled=false} en los eventos recurrentes "sync-owned" (con
     * {@code sysacadHash} no nulo) que no aparecieron en la corrida actual del sync. No destructivo:
     * nunca borra el evento, sus ocurrencias ni sus asignaciones. Devuelve la cantidad marcada.
     */
    int markRecurringEventsAbsent(Collection<Long> presentEventIds);

    /**
     * Ubica (sin crear) el {@code RecurringEvent} ya creado por el sync EVENTOS, por su clave natural
     * (subjectId, commissionId, dayOfWeek, startTime, startDate, endDate) — a diferencia de
     * {@link #findOrCreateRecurringEvent} (ingest) y {@link #syncRecurringEvent} (EVENTOS), esta lectura
     * nunca crea el evento: el sync ASIGNACIONES (SysAcad) sólo enlaza aula a un evento que EVENTOS ya
     * debió haber creado antes (ver .claude/docs/plan-sync-eventos-sysacad.md §3.3); vacío si no existe
     * todavía, para que el caller salte la fila con WARN en vez de crearlo acá.
     */
    Optional<Long> findRecurringEventId(Long subjectId, Long commissionId, DayOfWeek dayOfWeek,
            LocalTime startTime, LocalDate startDate, LocalDate endDate);

    AcademicEventResponseDto createUniqueEvent(CreateUniqueEventRequestDto dto);

    List<AcademicEventResponseDto> findUniqueEvents();

    AcademicEventResponseDto updateUniqueEvent(Long id, UpdateUniqueEventRequestDto dto);
}
