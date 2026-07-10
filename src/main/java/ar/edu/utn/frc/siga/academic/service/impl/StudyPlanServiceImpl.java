package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.StudyPlanResponseDto;
import ar.edu.utn.frc.siga.academic.mapper.StudyPlanMapper;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.academic.repository.StudyPlanRepository;
import ar.edu.utn.frc.siga.academic.service.StudyPlanService;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
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
    private final SpecialtyRepository specialtyRepository;
    private final StudyPlanMapper studyPlanMapper;

    @Override
    @Transactional
    public FindOrCreateResult<StudyPlanResponseDto> findOrCreate(Integer planCode, Integer specialtyCode) {
        Specialty specialty = requireSpecialty(specialtyCode);
        return FindOrCreateResult.resolve(
                studyPlanRepository.findByPlanCodeAndSpecialtyAndDeletedFalse(planCode, specialty),
                () -> {
                    log.info("Creando StudyPlan: code={}, specialty={}", planCode, specialty.getId());
                    return studyPlanRepository.save(
                            StudyPlan.builder()
                                    .planCode(planCode)
                                    .specialty(specialty)
                                    .build());
                }
        ).map(studyPlanMapper::toDto);
    }

    private Specialty requireSpecialty(Integer specialtyCode) {
        return specialtyRepository.findBySpecialtyCodeAndDeletedFalse(specialtyCode)
                .orElseThrow(() -> ResourceNotFoundException.of("Specialty", specialtyCode));
    }
}
