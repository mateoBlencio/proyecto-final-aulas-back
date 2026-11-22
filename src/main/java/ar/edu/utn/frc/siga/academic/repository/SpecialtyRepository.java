package ar.edu.utn.frc.siga.academic.repository;

import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.common.repository.SoftDeletableRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface SpecialtyRepository extends SoftDeletableRepository<Specialty, Long> {
    Optional<Specialty> findBySpecialtyCode(Integer specialtyCode);
}
