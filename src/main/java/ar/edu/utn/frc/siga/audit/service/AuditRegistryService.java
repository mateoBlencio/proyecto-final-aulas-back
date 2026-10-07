package ar.edu.utn.frc.siga.audit.service;

import ar.edu.utn.frc.siga.audit.dto.AuditLogFilter;
import ar.edu.utn.frc.siga.audit.dto.response.AuditLogEntryDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AuditRegistryService {

    /**
     * Paginated log, ordered by revision descending: business operations (one entry per
     * operation), transactions without an operation that touched several records (one entry per revision)
     * and standalone individual changes.
     */
    Page<AuditLogEntryDto> findAll(AuditLogFilter filter, Pageable pageable);

    /** Individual changes that make up an operation (drill-down), with optional filters. */
    Page<AuditLogEntryDto> findOperationItems(String operationId, AuditLogFilter filter, Pageable pageable);

    /** Individual changes of an Envers revision (one transaction), with optional filters. */
    Page<AuditLogEntryDto> findRevisionItems(int revision, AuditLogFilter filter, Pageable pageable);

    /** Etiquetas de dominio de las entidades auditadas, en el mismo orden del registry. */
    List<String> findEntityTypes();
}
