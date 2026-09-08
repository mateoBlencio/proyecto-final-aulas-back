package ar.edu.utn.frc.siga.audit.service;

import ar.edu.utn.frc.siga.audit.dto.AuditLogFilter;
import ar.edu.utn.frc.siga.audit.dto.response.AuditLogEntryDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditRegistryService {

    /**
     * Registro paginado: operaciones de negocio en lote (una entrada por operación) y cambios
     * individuales sueltos, ordenados por revisión descendente.
     */
    Page<AuditLogEntryDto> findAll(AuditLogFilter filter, Pageable pageable);

    /** Cambios individuales que componen una operación en lote (drill-down). */
    Page<AuditLogEntryDto> findOperationItems(String operationId, Pageable pageable);
}
