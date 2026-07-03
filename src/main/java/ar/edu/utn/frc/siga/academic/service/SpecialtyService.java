package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import java.util.Optional;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface SpecialtyService {

    Optional<Specialty> findBySpecialtyCodeAndDeletedFalse(Integer specialtyCode);

    Specialty save(Specialty specialty);

    FindOrCreateResult<Specialty> findOrCreate(Integer specialtyCode);
}
