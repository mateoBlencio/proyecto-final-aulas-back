package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.command.SubjectSyncCommand;
import ar.edu.utn.frc.siga.common.service.ActivationService;
import java.util.Collection;
import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface SubjectService extends ActivationService<Long> {

    List<SubjectResponseDto> findAll(boolean includeDeactivated);

    SubjectResponseDto findById(Long id);

    List<SubjectResponseDto> findByIds(Collection<Long> ids);

    SubjectResponseDto findByCodeAndStudyPlan(Integer code, Integer studyPlanCode, Integer specialtyCode);

    List<SubjectResponseDto> findBySpecialtyCode(Integer specialtyCode, boolean includeDeactivated);

    /**
     * Sincroniza el lote de materias provenientes de SysAcad: crea/actualiza por clave natural
     * (código + plan) comparando hash, y devuelve la cantidad de filas afectadas.
     */
    int syncSubjects(List<SubjectSyncCommand> commands);
}
