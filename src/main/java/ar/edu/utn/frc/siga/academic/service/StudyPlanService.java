package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.StudyPlanResponseDto;
import ar.edu.utn.frc.siga.common.service.ActivationService;

import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface StudyPlanService extends ActivationService<Long> {

    List<StudyPlanResponseDto> findAll();

    StudyPlanResponseDto findById(Long id);

    StudyPlanResponseDto findByPlanCodeAndSpecialtyCode(Integer planCode, Integer specialtyCode);
}
