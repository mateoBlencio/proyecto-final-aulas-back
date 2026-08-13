package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import java.util.Collection;
import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface SubjectService {

    List<SubjectResponseDto> findAll();

    SubjectResponseDto findById(Long id);

    List<SubjectResponseDto> findByIds(Collection<Long> ids);

    SubjectResponseDto findByCodeAndStudyPlan(Integer code, Integer studyPlanCode, Integer specialtyCode);

    List<SubjectResponseDto> findBySpecialtyCode(Integer specialtyCode);
}
