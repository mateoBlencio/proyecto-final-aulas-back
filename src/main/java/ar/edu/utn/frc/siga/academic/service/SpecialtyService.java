package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;

import org.springframework.modulith.NamedInterface;

/** Fachada de especialidades: resolución idempotente (find-or-create) por código de especialidad. */
@NamedInterface("api")
public interface SpecialtyService {

    FindOrCreateResult<SpecialtyResponseDto> findOrCreate(Integer specialtyCode);
}
