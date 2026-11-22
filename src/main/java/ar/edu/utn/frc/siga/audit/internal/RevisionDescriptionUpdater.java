package ar.edu.utn.frc.siga.audit.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Rewrites {@code revinfo.descripcion} for every revision of an operation when
 * {@code AuditOperations.describe} changed the text after the first revision was stamped.
 */
@Slf4j
@Component
public class RevisionDescriptionUpdater {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public RevisionDescriptionUpdater(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // Own transaction: an outer rollback must not drop the UPDATE and a failure must not poison the outer one.
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Does not propagate failures: the business operation already finished. */
    public void update(String operationId, String description) {
        try {
            transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(
                    "UPDATE revinfo SET descripcion = ? WHERE operacion_id = ?", description, operationId));
        } catch (RuntimeException e) {
            log.warn("No se pudo actualizar la descripción de la operación {}", operationId, e);
        }
    }
}
