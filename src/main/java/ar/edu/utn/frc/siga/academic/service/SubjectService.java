package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface SubjectService {

    Optional<Subject> findById(Long id);

    Subject save(Subject subject);

    FindOrCreateResult<Subject> findOrCreate(Integer code, String name, StudyPlan studyPlan, String term);

    List<SubjectResponseDto> findDtosByIds(Collection<Long> ids);

    SubjectResponseDto findDtoById(Long id);
}
