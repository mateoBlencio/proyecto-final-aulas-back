package ar.edu.utn.frc.classroom_allocation.career.service.impl;

import ar.edu.utn.frc.classroom_allocation.career.model.Specialty;
import ar.edu.utn.frc.classroom_allocation.career.model.StudyPlan;
import ar.edu.utn.frc.classroom_allocation.career.repository.StudyPlanRepository;
import ar.edu.utn.frc.classroom_allocation.career.service.StudyPlanService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StudyPlanServiceImpl implements StudyPlanService {

    private final StudyPlanRepository studyPlanRepository;

    @Override
    public Optional<StudyPlan> findByPlanCodeAndSpecialtyAndDeletedFalse(Integer planCode, Specialty specialty) {
        return studyPlanRepository.findByPlanCodeAndSpecialtyAndDeletedFalse(planCode, specialty);
    }

    @Override
    @Transactional
    public StudyPlan save(StudyPlan studyPlan) {
        return studyPlanRepository.save(studyPlan);
    }
}
