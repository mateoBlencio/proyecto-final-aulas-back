package ar.edu.utn.frc.siga.audit;

import org.springframework.modulith.NamedInterface;

import java.util.List;
import java.util.Map;

/**
 * Builds the human-readable label of the records of one audited entity. The module that owns the entity
 * implements it as a bean; {@code audit} never depends on the domain modules.
 *
 * <p>Implementations resolve the whole page in batch (one lookup per referenced catalog, never one per record)
 * and read the record's own fields from {@link AuditedRecord#values()}, so records that no longer exist are
 * labelled too.
 */
@NamedInterface("api")
public interface AuditLabelProvider {

    /** Root audited class this provider labels. */
    Class<?> entityType();

    /** Labels by record id. A record missing from the result has no label. */
    Map<String, String> labels(List<AuditedRecord> records);
}
