package ar.edu.utn.frc.siga.events.service;

import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.events.service.command.OccurrenceRelease;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface OccurrenceService {

    OccurrenceSlotDto findSlot(Long occurrenceId);

    List<OccurrenceSlotDto> findSlots(Collection<Long> occurrenceIds);

    /** Fecha de cada ocurrencia por id, sin cargar el evento (para etiquetas de auditoría). */
    Map<Long, LocalDate> findDatesByIds(Collection<Long> occurrenceIds);

    List<OccurrenceSlotDto> findSlotsByEvent(Long eventId, LocalDate from);

    /**
     * Crea {@code count} ocurrencias simultáneas a la de {@code occurrenceId}: mismo evento, misma
     * fecha, {@code roomSlot} correlativo a partir del máximo existente y {@code mirrorOfOccurrenceId}
     * apuntando a la principal. Es el único punto donde se crean ocurrencias simultáneas.
     */
    List<Long> createSimultaneous(Long occurrenceId, int count);

    List<OccurrenceSlotDto> findSlotsByEvents(Collection<Long> eventIds, LocalDate from);

    List<OccurrenceSlotDto> findSlotsByEvents(Collection<Long> eventIds);

    List<OccurrenceSlotDto> findSlotsByStatusBetween(OccurrenceStatus status, LocalDate from, LocalDate to);

    List<OccurrenceSlotDto> findSlotsBetween(LocalDate from, LocalDate to);

    List<OccurrenceSlotDto> findSlotsByDate(LocalDate date);

    boolean existsOccurrence(Long occurrenceId);

    /** Espejos de las ocurrencias principales indicadas (mismo evento y fecha, otro {@code roomSlot}). */
    List<OccurrenceSlotDto> findMirrors(Collection<Long> principalOccurrenceIds);

    /**
     * Pasa las ocurrencias a {@code ROOM_RELEASED} con motivo, nota y el aula que tenían (la aporta
     * quien borró la asignación). No toca asignaciones. Rechaza ocurrencias pasadas, motivo nulo y
     * {@code OTHER} sin nota.
     */
    void release(List<OccurrenceRelease> releases, ReleaseReason reason, String note);

    /** Vuelve a {@code NEEDS_ROOM}, limpia motivo y nota y conserva el aula anterior. */
    void requestRoom(Collection<Long> occurrenceIds);
}
