package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationHistorySnapshotDto;
import ar.edu.utn.frc.siga.common.dto.response.RevisionDto;

import java.util.List;

/**
 * Consulta del historial de auditoría (Hibernate Envers) de las asignaciones de aula:
 * quién cambió qué y cuándo, leyendo {@code asignacion_aula_aud} vía {@code AuditReader}.
 * Solo lectura; las revisiones las escribe Envers automáticamente en cada transacción.
 */
public interface AllocationAuditHistoryService {

    /**
     * Revisiones de la(s) allocation de TODAS las occurrences de un evento, fusionadas en una sola
     * línea de tiempo en orden de revisión ascendente: qué aula tuvo cada occurrence en cada
     * momento, origen y quién la cambió. Por evento y no por occurrenceId/allocationId porque la
     * allocation puede borrarse/recrearse y un evento recurrente tiene muchas occurrences; un
     * evento único tiene exactamente una occurrence, así que en ese caso esto degenera en el mismo
     * comportamiento de siempre para una sola ocurrencia. 404 si el evento no existe; lista vacía
     * si existe pero ninguna de sus occurrences tuvo nunca asignación.
     */
    List<RevisionDto<AllocationHistorySnapshotDto>> findAllocationHistory(Long eventId);
}
