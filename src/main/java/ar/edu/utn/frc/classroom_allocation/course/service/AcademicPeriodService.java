package ar.edu.utn.frc.classroom_allocation.course.service;

import ar.edu.utn.frc.classroom_allocation.course.model.AcademicPeriod;
import java.util.Optional;

public interface AcademicPeriodService {

    Optional<AcademicPeriod> findByYearAndSemester(Integer year, Integer semester);

    AcademicPeriod save(AcademicPeriod academicPeriod);
}
