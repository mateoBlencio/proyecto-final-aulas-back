package ar.edu.utn.frc.siga.audit.internal;

import ar.edu.utn.frc.siga.audit.model.ActorType;
import ar.edu.utn.frc.siga.audit.model.SigaRevision;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import ar.edu.utn.frc.siga.audit.model.RevisionSummaryKey;
import org.hibernate.envers.EntityTrackingRevisionListener;
import org.hibernate.envers.RevisionType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Besides stamping the revision, counts the rows audited per root table and type so the log listing
 * does not have to scan every {@code _aud} table. Counts in memory and does not query: a 52k-row
 * operation costs a map update per row and one insert per (table, type) at flush.
 * Every audited root entity needs an explicit {@code @Table(name)}: the {@code _aud} name derives from it.
 */
public class SigaRevisionListener implements EntityTrackingRevisionListener {

    private static final Map<Class<?>, String> ROOT_AUDIT_TABLES = new ConcurrentHashMap<>();

    @Override
    public void newRevision(Object revisionEntity) {
        SigaRevision revision = (SigaRevision) revisionEntity;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        boolean unauthenticated = authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken;

        revision.setUsuario(unauthenticated ? null : authentication.getName());
        // Without a session, a write inside an HTTP request comes from a public form (a person);
        // without a request either, it comes from a scheduler or an async listener.
        boolean system = unauthenticated && RequestContextHolder.getRequestAttributes() == null;
        revision.setActorType(system ? ActorType.SYSTEM : ActorType.HUMAN);

        AuditOperationContext.Operation operation = AuditOperationContext.current();
        if (operation != null) {
            revision.setOperacionId(operation.id());
            revision.setDescripcion(operation.description());
            revision.setParentOperationId(operation.parentId());
            AuditOperationContext.markStamped();
        }
    }

    @Override
    public void entityChanged(Class entityClass, String entityName, Object entityId,
                              RevisionType revisionType, Object revisionEntity) {
        ((SigaRevision) revisionEntity).getSummary().merge(
                new RevisionSummaryKey(rootAuditTable(entityClass), revisionType.getRepresentation()), 1, Integer::sum);
    }

    /** Subclasses (JOINED) count in the root's table, like the log listing, which only queries roots. */
    private static String rootAuditTable(Class<?> entityClass) {
        return ROOT_AUDIT_TABLES.computeIfAbsent(entityClass, type -> {
            Class<?> root = null;
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                if (c.isAnnotationPresent(Entity.class)) {
                    root = c;
                }
            }
            Table table = root == null ? null : root.getAnnotation(Table.class);
            if (table == null || table.name().isBlank()) {
                throw new IllegalStateException("La entidad auditada " + type.getName() + " necesita @Table(name)");
            }
            return table.name() + "_aud";
        });
    }
}
