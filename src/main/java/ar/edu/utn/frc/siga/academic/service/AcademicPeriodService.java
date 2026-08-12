package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.academic.model.TermType;

import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface AcademicPeriodService {

    FindOrCreateResult<AcademicPeriodResponseDto> findOrCreate(Integer year, TermType termType);

    List<AcademicPeriodResponseDto> findActive();

    List<AcademicPeriodResponseDto> findAll();

    AcademicPeriodResponseDto findById(Long id);
}
