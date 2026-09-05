package ar.edu.utn.frc.siga.academic.repository;

import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.common.repository.SoftDeletableRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

@Repository
public interface CommissionRepository extends SoftDeletableRepository<Commission, Long> {

    Optional<Commission> findByCourseCodeAndAcademicPeriod(
            String courseCode, AcademicPeriod academicPeriod);

    @Override
    @EntityGraph(attributePaths = {"academicPeriod"})
    Optional<Commission> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"academicPeriod"})
    List<Commission> findAllById(Iterable<Long> ids);

    @EntityGraph(attributePaths = {"academicPeriod"})
    List<Commission> findByCourseCodeAndSysacadEnabledTrueAndDeletedAtIsNull(String courseCode);
}
