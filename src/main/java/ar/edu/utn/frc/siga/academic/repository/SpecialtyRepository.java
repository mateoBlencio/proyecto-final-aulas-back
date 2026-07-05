package ar.edu.utn.frc.siga.academic.repository;

import ar.edu.utn.frc.siga.academic.model.Specialty;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpecialtyRepository extends JpaRepository<Specialty, Long> {
    Optional<Specialty> findBySpecialtyCodeAndDeletedFalse(Integer specialtyCode);
}
