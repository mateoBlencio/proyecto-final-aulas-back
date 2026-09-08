package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.AcademicPeriodFilter;
import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.common.service.ActivationService;
import ar.edu.utn.frc.siga.academic.model.TermType;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface AcademicPeriodService extends ActivationService<Long> {

    FindOrCreateResult<AcademicPeriodResponseDto> findOrCreate(Integer year, TermType termType);

    List<AcademicPeriodResponseDto> findActive();

    Page<AcademicPeriodResponseDto> findAll(AcademicPeriodFilter filter, Pageable pageable, boolean includeDeactivated);

    AcademicPeriodResponseDto findById(Long id);
}
