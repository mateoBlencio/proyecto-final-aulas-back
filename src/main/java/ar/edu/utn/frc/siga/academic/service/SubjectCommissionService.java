package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;

import org.springframework.modulith.NamedInterface;

/**
 * Fachada de la relación materia-comisión: resolución idempotente (find-or-create)
 * que registra cuántos inscriptos tiene una materia dictada en una comisión dada.
 */
@NamedInterface("api")
public interface SubjectCommissionService {

    FindOrCreateResult<SubjectCommissionResponseDto> findOrCreate(Long subjectId, Long commissionId, Integer enrolledCount);
}
