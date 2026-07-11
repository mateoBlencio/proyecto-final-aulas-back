package ar.edu.utn.frc.siga.space.repository;

import ar.edu.utn.frc.siga.space.model.ClassroomType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Acceso a datos de {@link ClassroomType}.
 */
@Repository
public interface ClassroomTypeRepository extends JpaRepository<ClassroomType, Integer> {

    /** Búsqueda case-insensitive por descripción, usada para resolver el tipo de aula por defecto. */
    Optional<ClassroomType> findByDescriptionIgnoreCase(String description);

}
