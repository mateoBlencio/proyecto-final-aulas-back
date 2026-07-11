package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.academic.model.TermType;

import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface AcademicPeriodService {

    FindOrCreateResult<AcademicPeriodResponseDto> findOrCreate(Integer year, TermType termType);

    /** Períodos académicos activos, usados por {@code allocation} para resolver el rango por defecto de "problems". */
    List<AcademicPeriodResponseDto> findActive();
}
