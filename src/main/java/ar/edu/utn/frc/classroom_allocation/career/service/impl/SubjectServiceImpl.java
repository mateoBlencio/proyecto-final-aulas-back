package ar.edu.utn.frc.classroom_allocation.career.service.impl;

import ar.edu.utn.frc.classroom_allocation.career.model.StudyPlan;
import ar.edu.utn.frc.classroom_allocation.career.model.Subject;
import ar.edu.utn.frc.classroom_allocation.career.repository.SubjectRepository;
import ar.edu.utn.frc.classroom_allocation.career.service.SubjectService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SubjectServiceImpl implements SubjectService {

    private final SubjectRepository subjectRepository;

    @Override
    public Optional<Subject> findByCodeAndStudyPlanAndDeletedFalse(Integer code, StudyPlan studyPlan) {
        log.debug("Finding Subject: code={}, studyPlanId={}", code, studyPlan.getId());
        return subjectRepository.findByCodeAndStudyPlanAndDeletedFalse(code, studyPlan);
    }

    @Override
    @Transactional
    public Subject save(Subject subject) {
        log.debug("Saving Subject: code={}", subject.getCode());
        Subject saved = subjectRepository.save(subject);
        log.info("Subject saved: id={}", saved.getId());
        return saved;
    }
}
