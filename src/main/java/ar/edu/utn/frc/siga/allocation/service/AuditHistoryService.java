package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationHistorySnapshotDto;
import ar.edu.utn.frc.siga.allocation.dto.response.EventHistorySnapshotDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceHistorySnapshotDto;
import ar.edu.utn.frc.siga.allocation.dto.response.RevisionDto;

import java.util.List;

/**
 * Consulta del historial de auditoría (Hibernate Envers) de las entidades del módulo:
 * quién cambió qué y cuándo, leyendo las tablas {@code _aud} vía {@code AuditReader}.
 * Solo lectura; las revisiones las escribe Envers automáticamente en cada transacción.
 */
public interface AuditHistoryService {

    /** Revisiones de un evento académico (campos propios + los del subtipo), orden ascendente. 404 si el evento no existe ni existió. */
    List<RevisionDto<EventHistorySnapshotDto>> findEventHistory(Long eventId);

    /** Revisiones de una ocurrencia (cambios de estado), orden ascendente. 404 si la ocurrencia no existe ni existió. */
    List<RevisionDto<OccurrenceHistorySnapshotDto>> findOccurrenceHistory(Long occurrenceId);

    /**
     * Revisiones de la(s) allocation de una ocurrencia: qué aula tuvo en cada momento, origen y
     * quién la cambió. Por ocurrencia y no por allocationId porque la allocation puede
     * borrarse/recrearse; la ocurrencia es el ancla estable. 404 si la ocurrencia no existe ni existió;
     * lista vacía si existe pero nunca tuvo asignación.
     */
    List<RevisionDto<AllocationHistorySnapshotDto>> findAllocationHistory(Long occurrenceId);
}
