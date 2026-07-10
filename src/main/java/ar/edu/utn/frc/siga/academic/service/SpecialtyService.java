package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface SpecialtyService {

    FindOrCreateResult<SpecialtyResponseDto> findOrCreate(Integer specialtyCode);
}
