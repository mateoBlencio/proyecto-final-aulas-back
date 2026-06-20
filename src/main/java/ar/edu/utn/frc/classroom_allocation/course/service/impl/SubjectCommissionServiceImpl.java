package ar.edu.utn.frc.classroom_allocation.course.service.impl;

import ar.edu.utn.frc.classroom_allocation.career.model.Subject;
import ar.edu.utn.frc.classroom_allocation.course.model.Commission;
import ar.edu.utn.frc.classroom_allocation.course.model.SubjectCommission;
import ar.edu.utn.frc.classroom_allocation.course.repository.SubjectCommissionRepository;
import ar.edu.utn.frc.classroom_allocation.course.service.SubjectCommissionService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SubjectCommissionServiceImpl implements SubjectCommissionService {

    private final SubjectCommissionRepository subjectCommissionRepository;

    @Override
    public Optional<SubjectCommission> findBySubjectAndCommissionAndDeletedFalse(Subject subject, Commission commission) {
        return subjectCommissionRepository.findBySubjectAndCommissionAndDeletedFalse(subject, commission);
    }

    @Override
    @Transactional
    public SubjectCommission save(SubjectCommission subjectCommission) {
        return subjectCommissionRepository.save(subjectCommission);
    }
}
