package ar.edu.utn.frc.siga.common.audit;

import ar.edu.utn.frc.siga.common.dto.response.RevisionDto;
import ar.edu.utn.frc.siga.common.dto.response.RevisionKind;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Lector genérico del historial de auditoría de Envers: dado un tipo de entidad, una propiedad
 * por la que filtrar y una función de mapeo a snapshot, arma la lista de {@link RevisionDto} en
 * orden de revisión ascendente. Reutilizable por cualquier módulo que audite una entidad propia,
 * sin exponer tipos de Envers (ni entidades JPA) fuera de esta clase.
 */
@Component
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

    /**
     * Como {@link #read(Class, String, Object, Function)} pero filtrando por varios valores de la
     * misma propiedad a la vez (IN en vez de =), con orden de revisión ascendente global entre
     * todos los valores — no por valor. Útil para fusionar el historial de varias entidades
     * relacionadas (p. ej. todas las occurrences de un mismo evento) en una sola línea de tiempo.
     */
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
