package ar.edu.utn.frc.classroom_allocation.course.service;

import ar.edu.utn.frc.classroom_allocation.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.classroom_allocation.course.model.AcademicPeriod;
import ar.edu.utn.frc.classroom_allocation.course.model.Commission;
import java.util.Optional;

public interface CommissionService {

    Optional<Commission> findByCourseCodeAndCommissionNumberAndPeriodAndDeletedFalse(
            String courseCode, Integer commissionNumber, AcademicPeriod period);

    Commission save(Commission commission);

    FindOrCreateResult<Commission> findOrCreate(String courseCode, Integer commissionNumber,
            Integer yearLevel, AcademicPeriod period);
}
