package ar.edu.utn.frc.classroom_allocation.career.repository;

import ar.edu.utn.frc.classroom_allocation.career.model.Specialty;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpecialtyRepository extends JpaRepository<Specialty, Long> {

    Optional<Specialty> findByCodigoEspecialidadAndDeletedFalse(Integer codigoEspecialidad);
}
