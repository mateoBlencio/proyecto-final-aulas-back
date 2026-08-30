package ar.edu.utn.frc.siga.common.repository;

import org.springframework.data.jpa.domain.Specification;

public final class SoftDeleteSpecifications {

    private SoftDeleteSpecifications() {}

    public static <T> Specification<T> active() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static <T> Specification<T> inactive() {
        return (root, query, cb) -> cb.isNotNull(root.get("deletedAt"));
    }
}
