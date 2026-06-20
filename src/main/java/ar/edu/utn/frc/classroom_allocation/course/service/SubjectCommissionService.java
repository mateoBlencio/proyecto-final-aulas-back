package ar.edu.utn.frc.classroom_allocation.course.service;

import ar.edu.utn.frc.classroom_allocation.career.model.Subject;
import ar.edu.utn.frc.classroom_allocation.course.model.Commission;
import ar.edu.utn.frc.classroom_allocation.course.model.SubjectCommission;
import java.util.Optional;

public interface SubjectCommissionService {

    Optional<SubjectCommission> findBySubjectAndCommissionAndDeletedFalse(Subject subject, Commission commission);

    SubjectCommission save(SubjectCommission subjectCommission);
}
