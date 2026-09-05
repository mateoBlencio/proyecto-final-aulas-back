package ar.edu.utn.frc.siga.common.repository;

import static ar.edu.utn.frc.siga.common.repository.SoftDeleteSpecifications.active;

import ar.edu.utn.frc.siga.common.model.SoftDeletableEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface SoftDeletableRepository<T extends SoftDeletableEntity, ID>
        extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {

    default List<T> findAllActive() {
        return findAll(active());
    }

    default Page<T> findAllActive(Pageable pageable) {
        return findAll(active(), pageable);
    }

    default Optional<T> findActiveById(ID id) {
        return findById(id).filter(SoftDeletableEntity::isActive);
    }

    default boolean existsActiveById(ID id) {
        return findActiveById(id).isPresent();
    }

    default T softDelete(T entity) {
        entity.deactivate();
        return save(entity);
    }

    default T restore(T entity) {
        entity.activate();
        return save(entity);
    }
}
