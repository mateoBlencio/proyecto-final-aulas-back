package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import java.util.Optional;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface SubjectCommissionService {

    Optional<SubjectCommission> findBySubjectAndCommissionAndDeletedFalse(Subject subject, Commission commission);

    SubjectCommission save(SubjectCommission subjectCommission);

    FindOrCreateResult<SubjectCommission> findOrCreate(Subject subject, Commission commission, Integer enrolledCount);
}
