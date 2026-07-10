package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import java.util.Collection;
import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface SubjectService {

    SubjectResponseDto findById(Long id);

    List<SubjectResponseDto> findByIds(Collection<Long> ids);

    /** {@code studyPlanCode}/{@code specialtyCode} identifican el plan por su clave natural compuesta. */
    FindOrCreateResult<SubjectResponseDto> findOrCreate(
            Integer code, String name, Integer studyPlanCode, Integer specialtyCode, String term);
}
