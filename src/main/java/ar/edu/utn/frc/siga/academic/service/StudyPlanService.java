package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.StudyPlanFilter;
import ar.edu.utn.frc.siga.academic.dto.response.StudyPlanResponseDto;
import ar.edu.utn.frc.siga.common.service.ActivationService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface StudyPlanService extends ActivationService<Long> {

    Page<StudyPlanResponseDto> findAll(StudyPlanFilter filter, Pageable pageable, boolean includeDeactivated);

    StudyPlanResponseDto findById(Long id);

    StudyPlanResponseDto findByPlanCodeAndSpecialtyCode(Integer planCode, Integer specialtyCode);
}
