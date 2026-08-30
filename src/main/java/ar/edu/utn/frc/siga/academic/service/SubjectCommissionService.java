package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.academic.model.SubjectCommissionId;
import ar.edu.utn.frc.siga.common.service.ActivationService;

import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface SubjectCommissionService extends ActivationService<SubjectCommissionId> {

    SubjectCommissionResponseDto findBySubjectAndCommission(Long subjectId, Long commissionId);

    /**
     * Resuelve el link materia-comisión ya creado por el sync de Comisiones, buscando la materia por
     * su código de SysAcad dentro de esa comisión — el mock de Eventos solo trae {@code courseCode}
     * (resuelto a {@code commissionId}) y {@code materia} (código), no el {@code subjectId} ya resuelto
     * que pide {@link #findBySubjectAndCommission}. Ausente → 404 catcheable, para que el sync de
     * Eventos salte la fila con WARN.
     */
    SubjectCommissionResponseDto findByCommissionAndSubjectCode(Long commissionId, Integer subjectCode);

    List<SubjectCommissionResponseDto> findAll(boolean includeDeactivated);

    List<SubjectCommissionResponseDto> findBySubjectId(Long subjectId, boolean includeDeactivated);
}
