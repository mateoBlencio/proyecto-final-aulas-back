package ar.edu.utn.frc.siga.audit;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import lombok.RequiredArgsConstructor;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.envers.query.AuditQuery;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

@Component
@NamedInterface("api")
@RequiredArgsConstructor
public class RevisionReader {

    private final EntityManager entityManager;

    public <E, S> List<RevisionDto<S>> read(Class<E> entityClass, String property, Object value, Function<E, S> toSnapshot) {
        List<?> results = AuditReaderFactory.get(entityManager)
                .createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.property(property).eq(value))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        return toRevisionDtos(results, toSnapshot);
    }

    public <E, S> List<RevisionDto<S>> readById(Class<E> entityClass, Object id, Function<E, S> toSnapshot) {
        List<?> results = AuditReaderFactory.get(entityManager)
                .createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.id().eq(id))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        return toRevisionDtos(results, toSnapshot);
    }

    public <E, S> List<RevisionDto<S>> read(Class<E> entityClass, String property, Collection<?> values, Function<E, S> toSnapshot) {
        if (values.isEmpty()) {
            return List.of();
        }

        List<?> results = AuditReaderFactory.get(entityManager)
                .createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.property(property).in(values))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        return toRevisionDtos(results, toSnapshot);
    }

    public List<RevisionMetadata> readMetadata(Class<?> entityClass, LocalDateTime from, LocalDateTime to,
                                               String user, RevisionKind kind, String operationId) {
        AuditQuery query = AuditReaderFactory.get(entityManager)
                .createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .addOrder(AuditEntity.revisionNumber().asc());

        if (from != null) {
            query.add(AuditEntity.revisionProperty("fechaRevision").ge(from));
        }
        if (to != null) {
            query.add(AuditEntity.revisionProperty("fechaRevision").lt(to));
        }
        if (user != null) {
            query.add(AuditEntity.revisionProperty("usuario").eq(user));
        }
        if (kind != null) {
            query.add(AuditEntity.revisionType().eq(toType(kind)));
        }
        if (operationId != null) {
            query.add(AuditEntity.revisionProperty("operacionId").eq(operationId));
        }

        PersistenceUnitUtil idUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        return query.getResultList().stream()
                .map(row -> {
                    Object[] tuple = (Object[]) row;
                    SigaRevision revision = (SigaRevision) tuple[1];
                    RevisionType revisionType = (RevisionType) tuple[2];
                    String recordId = tuple[0] == null
                            ? null
                            : String.valueOf(idUtil.getIdentifier(tuple[0]));
                    return new RevisionMetadata(
                            recordId,
                            revision.getId(),
                            revision.getFechaRevision(),
                            revision.getUsuario(),
                            toKind(revisionType),
                            revision.getDescripcion(),
                            revision.getOperacionId());
                })
                .toList();
    }

    private <E, S> List<RevisionDto<S>> toRevisionDtos(List<?> results, Function<E, S> toSnapshot) {
        return results.stream()
                .map(row -> {
                    Object[] tuple = (Object[]) row;
                    SigaRevision revision = (SigaRevision) tuple[1];
                    RevisionType revisionType = (RevisionType) tuple[2];
                    @SuppressWarnings("unchecked")
                    E entity = (E) tuple[0];
                    return new RevisionDto<>(
                            revision.getId(),
                            revision.getFechaRevision(),
                            revision.getUsuario(),
                            toKind(revisionType),
                            revisionType == RevisionType.DEL ? null : toSnapshot.apply(entity));
                })
                .toList();
    }

    private RevisionKind toKind(RevisionType revisionType) {
        return switch (revisionType) {
            case ADD -> RevisionKind.CREATED;
            case MOD -> RevisionKind.MODIFIED;
            case DEL -> RevisionKind.DELETED;
        };
    }

    private RevisionType toType(RevisionKind kind) {
        return switch (kind) {
            case CREATED -> RevisionType.ADD;
            case MODIFIED -> RevisionType.MOD;
            case DELETED -> RevisionType.DEL;
        };
    }
}
