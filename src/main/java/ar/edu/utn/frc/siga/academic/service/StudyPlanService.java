package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.StudyPlanResponseDto;

import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface StudyPlanService {

    List<StudyPlanResponseDto> findAll();

    StudyPlanResponseDto findById(Long id);

    StudyPlanResponseDto findByPlanCodeAndSpecialtyCode(Integer planCode, Integer specialtyCode);
}
