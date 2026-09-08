package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.StudyPlanFilter;
import ar.edu.utn.frc.siga.academic.dto.response.StudyPlanResponseDto;
import ar.edu.utn.frc.siga.academic.mapper.StudyPlanMapper;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.academic.repository.StudyPlanRepository;
import ar.edu.utn.frc.siga.academic.service.StudyPlanService;
import ar.edu.utn.frc.siga.academic.specification.StudyPlanSpecification;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.repository.SoftDeleteSpecifications;
import ar.edu.utn.frc.siga.common.util.Finder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    public Page<StudyPlanResponseDto> findAll(StudyPlanFilter filter, Pageable pageable, boolean includeDeactivated) {
        return studyPlanRepository.findAll(
                        StudyPlanSpecification.withFilter(filter)
                                .and(SoftDeleteSpecifications.activeUnless(includeDeactivated)),
                        pageable)
                .map(studyPlanMapper::toDto);
    }

    @Override
    public StudyPlanResponseDto findById(Long id) {
        return studyPlanMapper.toDto(studyPlanRepository.findActiveById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("StudyPlan", id)));
    }

    @Override
    @Transactional
    public void activate(Long id) {
        studyPlanRepository.restore(Finder.orThrow(studyPlanRepository::findById, id, "StudyPlan"));
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        studyPlanRepository.softDelete(Finder.orThrow(studyPlanRepository::findById, id, "StudyPlan"));
    }

    @Override
    public StudyPlanResponseDto findByPlanCodeAndSpecialtyCode(Integer planCode, Integer specialtyCode) {
        Specialty specialty = requireSpecialty(specialtyCode);
        return studyPlanMapper.toDto(studyPlanRepository.findByPlanCodeAndSpecialtyAndDeletedAtIsNull(planCode, specialty)
                .orElseThrow(() -> ResourceNotFoundException.of("StudyPlan", planCode)));
    }

    private Specialty requireSpecialty(Integer specialtyCode) {
        return specialtyRepository.findBySpecialtyCode(specialtyCode)
                .orElseThrow(() -> ResourceNotFoundException.of("Specialty", specialtyCode));
    }
}
