package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.StudyPlanResponseDto;
import ar.edu.utn.frc.siga.academic.mapper.StudyPlanMapper;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.academic.repository.StudyPlanRepository;
import ar.edu.utn.frc.siga.academic.service.StudyPlanService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import java.util.List;
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
    public List<StudyPlanResponseDto> findAll() {
        return studyPlanRepository.findAll().stream()
                .map(studyPlanMapper::toDto)
                .toList();
    }

    @Override
    public StudyPlanResponseDto findById(Long id) {
        return studyPlanMapper.toDto(studyPlanRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("StudyPlan", id)));
    }

    @Override
    public StudyPlanResponseDto findByPlanCodeAndSpecialtyCode(Integer planCode, Integer specialtyCode) {
        Specialty specialty = requireSpecialty(specialtyCode);
        return studyPlanMapper.toDto(studyPlanRepository.findByPlanCodeAndSpecialty(planCode, specialty)
                .orElseThrow(() -> ResourceNotFoundException.of("StudyPlan", planCode)));
    }

    private Specialty requireSpecialty(Integer specialtyCode) {
        return specialtyRepository.findBySpecialtyCode(specialtyCode)
                .orElseThrow(() -> ResourceNotFoundException.of("Specialty", specialtyCode));
    }
}
