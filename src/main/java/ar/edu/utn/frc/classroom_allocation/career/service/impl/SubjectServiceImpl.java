package ar.edu.utn.frc.classroom_allocation.career.service.impl;

import ar.edu.utn.frc.classroom_allocation.career.model.StudyPlan;
import ar.edu.utn.frc.classroom_allocation.career.model.Subject;
import ar.edu.utn.frc.classroom_allocation.career.repository.SubjectRepository;
import ar.edu.utn.frc.classroom_allocation.career.service.SubjectService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SubjectServiceImpl implements SubjectService {

    private final SubjectRepository subjectRepository;

    @Override
    public Optional<Subject> findByCodeAndStudyPlanAndDeletedFalse(Integer code, StudyPlan studyPlan) {
        return subjectRepository.findByCodeAndStudyPlanAndDeletedFalse(code, studyPlan);
    }

    @Override
    @Transactional
    public Subject save(Subject subject) {
        return subjectRepository.save(subject);
    }
}
