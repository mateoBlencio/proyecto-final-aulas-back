package ar.edu.utn.frc.siga.space.repository;

import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Acceso a datos de {@link Classroom}, incluyendo búsqueda dinámica vía {@link JpaSpecificationExecutor}
 * para el filtro combinable de {@code ClassroomSpecification}.
 */
@Repository
public interface ClassroomRepository extends JpaRepository<Classroom, Integer>, JpaSpecificationExecutor<Classroom> {

    Optional<Classroom> findByRoomNumber(String roomNumber);

    /** Búsqueda por número de aula dentro de un edificio puntual, usada para find-or-create. */
    Optional<Classroom> findByRoomNumberAndBuilding(String roomNumber, Building building);

    /** Aulas habilitadas para asignación (no eliminadas y marcadas {@code available}). */
    List<Classroom> findByAvailableTrue();
}
