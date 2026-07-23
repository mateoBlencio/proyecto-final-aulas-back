package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.mapper.SubjectMapper;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.academic.repository.StudyPlanRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import java.util.Collection;
import java.util.List;
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
    private final StudyPlanRepository studyPlanRepository;
    private final SpecialtyRepository specialtyRepository;
    private final SubjectMapper subjectMapper;

    @Override
    public List<SubjectResponseDto> findAll() {
        return subjectRepository.findAll().stream()
                .map(subjectMapper::toDto)
                .toList();
    }

    @Override
    public SubjectResponseDto findById(Long id) {
        return subjectMapper.toDto(subjectRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Subject", id)));
    }

    @Override
    public List<SubjectResponseDto> findByIds(Collection<Long> ids) {
        return subjectRepository.findAllById(ids).stream()
                .map(subjectMapper::toDto)
                .toList();
    }

    @Override
    public SubjectResponseDto findByCodeAndStudyPlan(Integer code, Integer studyPlanCode, Integer specialtyCode) {
        StudyPlan studyPlan = requireStudyPlan(studyPlanCode, specialtyCode);
        return subjectMapper.toDto(subjectRepository.findByCodeAndStudyPlan(code, studyPlan)
                .orElseThrow(() -> ResourceNotFoundException.of("Subject", code)));
    }

    private StudyPlan requireStudyPlan(Integer studyPlanCode, Integer specialtyCode) {
        Specialty specialty = specialtyRepository.findBySpecialtyCode(specialtyCode)
                .orElseThrow(() -> ResourceNotFoundException.of("Specialty", specialtyCode));
        return studyPlanRepository.findByPlanCodeAndSpecialty(studyPlanCode, specialty)
                .orElseThrow(() -> ResourceNotFoundException.of("StudyPlan", studyPlanCode));
    }
}
