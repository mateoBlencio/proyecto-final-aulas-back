package ar.edu.utn.frc.siga.academic.repository;

import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import ar.edu.utn.frc.siga.academic.model.SubjectCommissionId;
import ar.edu.utn.frc.siga.common.repository.SoftDeletableRepository;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SubjectCommissionRepository
        extends SoftDeletableRepository<SubjectCommission, SubjectCommissionId> {

    @EntityGraph(attributePaths = {"commission", "commission.academicPeriod"})
    Optional<SubjectCommission> findBySubjectAndCommissionAndDeletedAtIsNull(Subject subject, Commission commission);

    @Query("""
            select distinct sc.commission.id, sp.specialtyCode, sp.name
            from SubjectCommission sc
            join sc.subject s
            join s.studyPlan p
            join p.specialty sp
            where sc.commission.id in :commissionIds
              and sc.deletedAt is null
              and s.deletedAt is null
            order by sp.specialtyCode
            """)
    List<Object[]> findSpecialtiesByCommissionIds(Collection<Long> commissionIds);

    @Override
    @EntityGraph(attributePaths = {"commission", "commission.academicPeriod"})
    List<SubjectCommission> findAll();

    @EntityGraph(attributePaths = {"commission", "commission.academicPeriod"})
    List<SubjectCommission> findBySubject_Id(Long subjectId);

    // A commission can be linked to two subjects with the same code under different study plans: the
    // service disambiguates by specialty and study plan.
    @EntityGraph(attributePaths = {"commission", "commission.academicPeriod"})
    List<SubjectCommission> findByCommission_IdAndSubject_CodeAndDeletedAtIsNull(
            Long commissionId, Integer subjectCode);
}
