package ar.edu.utn.frc.classroom_allocation.course.service;

import ar.edu.utn.frc.classroom_allocation.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.classroom_allocation.course.model.AcademicPeriod;
import ar.edu.utn.frc.classroom_allocation.course.model.TermType;
import java.util.Optional;

public interface AcademicPeriodService {

    Optional<AcademicPeriod> findByYearAndSemester(Integer year, Integer semester);

    AcademicPeriod save(AcademicPeriod academicPeriod);

    FindOrCreateResult<AcademicPeriod> findOrCreate(Integer year, TermType termType);
}
