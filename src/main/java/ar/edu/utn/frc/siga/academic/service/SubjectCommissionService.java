package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.SubjectCommissionFilter;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.academic.model.SubjectCommissionId;
import ar.edu.utn.frc.siga.common.service.ActivationService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface SubjectCommissionService extends ActivationService<SubjectCommissionId> {

    SubjectCommissionResponseDto findBySubjectAndCommission(Long subjectId, Long commissionId);

    /**
     * Resuelve el link materia-comisión ya creado por el sync de Comisiones, buscando la materia por
     * su código de SysAcad dentro de esa comisión — Horarios solo trae {@code courseCode} (resuelto a
     * {@code commissionId}), la materia (código) y su especialidad y plan, no el {@code subjectId} ya
     * resuelto que pide {@link #findBySubjectAndCommission}. Prefiere la materia de esa especialidad y
     * plan; si ninguna coincide y hay una sola candidata, la usa. Ausente o ambigua (más de una sin
     * coincidencia exacta) → 404 catcheable, para que el sync salte la fila con WARN.
     */
    SubjectCommissionResponseDto findByCommissionAndSubjectCode(Long commissionId, Integer subjectCode,
            Integer specialtyCode, Integer studyPlanCode);

    Page<SubjectCommissionResponseDto> findAll(SubjectCommissionFilter filter, Pageable pageable,
            boolean includeDeactivated);
}
