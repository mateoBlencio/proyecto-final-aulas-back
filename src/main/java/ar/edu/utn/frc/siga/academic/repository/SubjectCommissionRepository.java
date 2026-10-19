package ar.edu.utn.frc.siga.academic.repository;

import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import ar.edu.utn.frc.siga.academic.model.SubjectCommissionId;
import ar.edu.utn.frc.siga.common.repository.SoftDeletableRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

@Repository
public interface SubjectCommissionRepository
        extends SoftDeletableRepository<SubjectCommission, SubjectCommissionId> {

    @EntityGraph(attributePaths = {"commission", "commission.academicPeriod"})
    Optional<SubjectCommission> findBySubjectAndCommissionAndDeletedAtIsNull(Subject subject, Commission commission);

    @Override
    @EntityGraph(attributePaths = {"commission", "commission.academicPeriod"})
    List<SubjectCommission> findAll();

    @EntityGraph(attributePaths = {"commission", "commission.academicPeriod"})
    List<SubjectCommission> findBySubject_Id(Long subjectId);

    // Una comisión puede quedar linkeada a dos materias con el mismo código bajo planes distintos: el
    // service desambigua por especialidad y plan.
    @EntityGraph(attributePaths = {"commission", "commission.academicPeriod"})
    List<SubjectCommission> findByCommission_IdAndSubject_CodeAndDeletedAtIsNull(
            Long commissionId, Integer subjectCode);
}
