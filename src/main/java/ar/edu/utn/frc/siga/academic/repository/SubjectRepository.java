package ar.edu.utn.frc.siga.academic.repository;

import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.common.repository.SoftDeletableRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SubjectRepository extends SoftDeletableRepository<Subject, Long> {
    Optional<Subject> findByCodeAndStudyPlanAndDeletedAtIsNull(Integer code, StudyPlan studyPlan);

    // Same criteria as GET /subjects?specialtyCode=: one row per specialty. namePattern is a lowercase LIKE pattern.
    @Query("""
            select sp.id, count(s.id)
            from Subject s
            join s.studyPlan p
            join p.specialty sp
            where sp.id in :specialtyIds
              and (:subjectCode is null or s.code = :subjectCode)
              and lower(s.name) like :namePattern
              and (:studyPlanId is null or p.id = :studyPlanId)
              and (:includeDeactivated = true
                   or (s.deletedAt is null and p.deletedAt is null and sp.deletedAt is null))
            group by sp.id
            """)
    List<Object[]> countSubjectsBySpecialtyIds(Collection<Long> specialtyIds, Integer subjectCode,
            String namePattern, Long studyPlanId, boolean includeDeactivated);

    @Override
    @EntityGraph(attributePaths = {"studyPlan", "studyPlan.specialty"})
    Optional<Subject> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"studyPlan", "studyPlan.specialty"})
    List<Subject> findAllById(Iterable<Long> ids);

    @EntityGraph(attributePaths = {"studyPlan", "studyPlan.specialty"})
    List<Subject> findByStudyPlan_Specialty_SpecialtyCode(Integer specialtyCode);
}
