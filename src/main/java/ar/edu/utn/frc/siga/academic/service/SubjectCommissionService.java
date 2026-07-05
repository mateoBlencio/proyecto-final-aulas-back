package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface SubjectCommissionService {

    SubjectCommission save(SubjectCommission subjectCommission);

    FindOrCreateResult<SubjectCommission> findOrCreate(Subject subject, Commission commission, Integer enrolledCount);
}
