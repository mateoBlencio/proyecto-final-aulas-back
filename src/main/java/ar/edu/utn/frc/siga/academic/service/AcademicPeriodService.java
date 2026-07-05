package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.TermType;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface AcademicPeriodService {

    AcademicPeriod save(AcademicPeriod academicPeriod);

    FindOrCreateResult<AcademicPeriod> findOrCreate(Integer year, TermType termType);
}
