package ar.edu.utn.frc.siga.academic.repository;

import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import ar.edu.utn.frc.siga.academic.model.SubjectCommissionId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubjectCommissionRepository extends JpaRepository<SubjectCommission, SubjectCommissionId> {

    @EntityGraph(attributePaths = {"commission", "commission.academicPeriod"})
    Optional<SubjectCommission> findBySubjectAndCommission(Subject subject, Commission commission);

    @Override
    @EntityGraph(attributePaths = {"commission", "commission.academicPeriod"})
    List<SubjectCommission> findAll();

    @EntityGraph(attributePaths = {"commission", "commission.academicPeriod"})
    List<SubjectCommission> findBySubject_Id(Long subjectId);
}
