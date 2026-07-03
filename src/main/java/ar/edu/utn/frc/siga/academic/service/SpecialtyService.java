package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface SpecialtyService {

    Specialty save(Specialty specialty);

    FindOrCreateResult<Specialty> findOrCreate(Integer specialtyCode);
}
