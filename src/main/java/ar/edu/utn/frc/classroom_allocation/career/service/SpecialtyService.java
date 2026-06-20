package ar.edu.utn.frc.classroom_allocation.career.service;

import ar.edu.utn.frc.classroom_allocation.career.model.Specialty;
import java.util.Optional;

public interface SpecialtyService {

    Optional<Specialty> findBySpecialtyCodeAndDeletedFalse(Integer specialtyCode);

    Specialty save(Specialty specialty);
}
