package ar.edu.utn.frc.siga.events.service;

import ar.edu.utn.frc.siga.events.dto.response.EventHistorySnapshotDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceHistorySnapshotDto;
import ar.edu.utn.frc.siga.common.dto.response.RevisionDto;

import java.util.List;

import org.springframework.modulith.NamedInterface;

/**
 * Consulta del historial de auditoría (Hibernate Envers) de evento académico y occurrence:
 * quién cambió qué y cuándo, leyendo las tablas {@code _aud} vía {@code AuditReader}.
 * Solo lectura; las revisiones las escribe Envers automáticamente en cada transacción.
 */
@NamedInterface("api")
public interface EventAuditHistoryService {

    /** Revisiones de un evento académico (campos propios + los del subtipo), orden ascendente. 404 si el evento no existe ni existió. */
    List<RevisionDto<EventHistorySnapshotDto>> findEventHistory(Long eventId);

    /** Revisiones de una ocurrencia (cambios de estado), orden ascendente. 404 si la ocurrencia no existe ni existió. */
    List<RevisionDto<OccurrenceHistorySnapshotDto>> findOccurrenceHistory(Long occurrenceId);
}
