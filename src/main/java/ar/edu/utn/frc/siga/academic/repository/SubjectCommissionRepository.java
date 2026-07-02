package ar.edu.utn.frc.siga.academic.repository;

import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubjectCommissionRepository extends JpaRepository<SubjectCommission, Long> {

    Optional<SubjectCommission> findBySubjectAndCommissionAndDeletedFalse(Subject subject, Commission commission);
}
