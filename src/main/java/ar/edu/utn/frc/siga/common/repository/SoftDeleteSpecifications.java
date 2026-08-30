package ar.edu.utn.frc.siga.common.repository;

import org.springframework.data.jpa.domain.Specification;

/**
 * Specifications reutilizables para filtrar por estado de borrado lógico. Se componen con los
 * filtros existentes de cada entidad, p. ej.
 * {@code ClassroomSpecification.withFilter(filter).and(active())}.
 */
public final class SoftDeleteSpecifications {

    private SoftDeleteSpecifications() {}

    /** Solo entidades activas ({@code eliminado_en IS NULL}). */
    public static <T> Specification<T> active() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    /** Solo entidades borradas ({@code eliminado_en IS NOT NULL}). */
    public static <T> Specification<T> inactive() {
        return (root, query, cb) -> cb.isNotNull(root.get("deletedAt"));
    }
}
