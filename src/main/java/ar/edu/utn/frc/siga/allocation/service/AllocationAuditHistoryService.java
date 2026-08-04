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
     * Revisiones de la(s) allocation de una ocurrencia: qué aula tuvo en cada momento, origen y
     * quién la cambió. Por ocurrencia y no por allocationId porque la allocation puede
     * borrarse/recrearse; la ocurrencia es el ancla estable. 404 si la ocurrencia no existe ni existió;
     * lista vacía si existe pero nunca tuvo asignación.
     */
    List<RevisionDto<AllocationHistorySnapshotDto>> findAllocationHistory(Long occurrenceId);
}
