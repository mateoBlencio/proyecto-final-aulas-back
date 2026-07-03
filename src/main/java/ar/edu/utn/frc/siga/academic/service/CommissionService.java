package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import java.util.Optional;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface CommissionService {

    Optional<Commission> findByCourseCodeAndCommissionNumberAndPeriodAndDeletedFalse(
            String courseCode, Integer commissionNumber, AcademicPeriod period);

    Optional<Commission> findById(Long id);

    Commission save(Commission commission);

    FindOrCreateResult<Commission> findOrCreate(String courseCode, Integer commissionNumber,
            Integer yearLevel, AcademicPeriod period);
}
