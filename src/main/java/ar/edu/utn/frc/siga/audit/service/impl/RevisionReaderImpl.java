package ar.edu.utn.frc.siga.audit.service.impl;

import ar.edu.utn.frc.siga.audit.dto.response.RevisionDto;
import ar.edu.utn.frc.siga.audit.model.RevisionKind;
import ar.edu.utn.frc.siga.audit.model.SigaRevision;
import ar.edu.utn.frc.siga.audit.service.RevisionReader;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class RevisionReaderImpl implements RevisionReader {

    private final EntityManager entityManager;

    @Override
    public <E, S> List<RevisionDto<S>> read(Class<E> entityClass, String property, Object value, Function<E, S> toSnapshot) {
        List<?> results = AuditReaderFactory.get(entityManager)
                .createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.property(property).eq(value))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        return toRevisionDtos(results, toSnapshot);
    }

    @Override
    public <E, S> List<RevisionDto<S>> readById(Class<E> entityClass, Object id, Function<E, S> toSnapshot) {
        List<?> results = AuditReaderFactory.get(entityManager)
                .createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.id().eq(id))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        return toRevisionDtos(results, toSnapshot);
    }

    @Override
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
}
