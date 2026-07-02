package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.TermType;
import java.util.Optional;

public interface AcademicPeriodService {

    Optional<AcademicPeriod> findByYearAndSemester(Integer year, Integer semester);

    AcademicPeriod save(AcademicPeriod academicPeriod);

    FindOrCreateResult<AcademicPeriod> findOrCreate(Integer year, TermType termType);
}
