package ar.edu.utn.frc.siga.academic.repository;

import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommissionRepository extends JpaRepository<Commission, Long> {
    Optional<Commission> findByCourseCodeAndAcademicPeriod(
            String courseCode, AcademicPeriod academicPeriod);

    @Override
    @EntityGraph(attributePaths = {"academicPeriod"})
    Optional<Commission> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"academicPeriod"})
    List<Commission> findAllById(Iterable<Long> ids);

    @EntityGraph(attributePaths = {"academicPeriod"})
    List<Commission> findByCourseCodeAndSysacadEnabledTrue(String courseCode);
}
