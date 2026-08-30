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

/**
 * Fragment base para repositorios de entidades con borrado lógico. Expone las operaciones de
 * "solo activos" y de restore como métodos {@code default} sobre {@link JpaSpecificationExecutor}.
 *
 * <p>{@code findById} normal (sin {@code @SQLRestriction}) ve borrados → habilita restore. Los
 * servicios usan {@link #findActiveById(Object)} para el camino "solo activos" y {@code findById}
 * para reactivar/sync.
 */
@NoRepositoryBean
public interface SoftDeletableRepository<T extends SoftDeletableEntity, ID>
        extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {

    /** Todas las entidades activas. */
    default List<T> findAllActive() {
        return findAll(active());
    }

    /** Página de entidades activas. */
    default Page<T> findAllActive(Pageable pageable) {
        return findAll(active(), pageable);
    }

    /** Busca por id solo si la entidad está activa. */
    default Optional<T> findActiveById(ID id) {
        return findById(id).filter(SoftDeletableEntity::isActive);
    }

    /** Indica si existe una entidad activa con ese id. */
    default boolean existsActiveById(ID id) {
        return findActiveById(id).isPresent();
    }

    /** Soft delete + persist. Devuelve la entidad actualizada. */
    default T softDelete(T entity) {
        entity.deactivate();
        return save(entity);
    }

    /** Restore + persist. Devuelve la entidad actualizada. */
    default T restore(T entity) {
        entity.activate();
        return save(entity);
    }
}
