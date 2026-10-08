package ar.edu.utn.frc.siga.audit.service;

import ar.edu.utn.frc.siga.audit.dto.AuditLogFilter;
import ar.edu.utn.frc.siga.audit.dto.response.AuditLogEntryDto;
import ar.edu.utn.frc.siga.audit.dto.response.AuditOperationChainDto;
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

    /**
     * Changes of one record across all its revisions, with per-field diff, revision descending.
     * 400 if the entity type is unknown or {@code recordId} cannot be converted to the entity's id type;
     * empty page if the record has no revisions.
     */
    Page<AuditLogEntryDto> findEntityHistory(String entityType, String recordId, Pageable pageable);

    /**
     * Operations of the causal chain that contains {@code operationId} (ancestors and descendants), cause first.
     * Capped at 200 operations; {@code truncated} flags an incomplete chain. Empty if the id does not exist.
     */
    AuditOperationChainDto findOperationChain(String operationId);

    /** Etiquetas de dominio de las entidades auditadas, en el mismo orden del registry. */
    List<String> findEntityTypes();
}
