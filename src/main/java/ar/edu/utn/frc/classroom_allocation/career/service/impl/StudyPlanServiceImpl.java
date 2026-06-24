package ar.edu.utn.frc.classroom_allocation.career.service.impl;

import ar.edu.utn.frc.classroom_allocation.career.model.Specialty;
import ar.edu.utn.frc.classroom_allocation.career.model.StudyPlan;
import ar.edu.utn.frc.classroom_allocation.career.repository.StudyPlanRepository;
import ar.edu.utn.frc.classroom_allocation.career.service.StudyPlanService;
import ar.edu.utn.frc.classroom_allocation.common.dto.FindOrCreateResult;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StudyPlanServiceImpl implements StudyPlanService {

    private final StudyPlanRepository studyPlanRepository;

    @Override
    public Optional<StudyPlan> findByPlanCodeAndSpecialtyAndDeletedFalse(Integer planCode, Specialty specialty) {
        log.debug("Finding StudyPlan: planCode={}, specialtyId={}", planCode, specialty.getId());
        return studyPlanRepository.findByPlanCodeAndSpecialtyAndDeletedFalse(planCode, specialty);
    }

    @Override
    @Transactional
    public StudyPlan save(StudyPlan studyPlan) {
        log.debug("Saving StudyPlan: planCode={}", studyPlan.getPlanCode());
        StudyPlan saved = studyPlanRepository.save(studyPlan);
        log.info("StudyPlan saved: id={}", saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public FindOrCreateResult<StudyPlan> findOrCreate(Integer planCode, Specialty specialty) {
        return studyPlanRepository.findByPlanCodeAndSpecialtyAndDeletedFalse(planCode, specialty)
            .map(found -> new FindOrCreateResult<>(found, false))
            .orElseGet(() -> {
                log.info("Creando StudyPlan: code={}, specialty={}", planCode, specialty.getId());
                StudyPlan created = studyPlanRepository.save(
                    StudyPlan.builder()
                        .planCode(planCode)
                        .specialty(specialty)
                        .build()
                );
                return new FindOrCreateResult<>(created, true);
            });
    }
}
