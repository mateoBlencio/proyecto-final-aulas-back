package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.AcademicPeriodFilter;
import ar.edu.utn.frc.siga.academic.dto.request.UpdateAcademicPeriodRequestDto;
import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.common.service.ActivationService;
import ar.edu.utn.frc.siga.academic.model.TermType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface AcademicPeriodService extends ActivationService<Long> {

    FindOrCreateResult<AcademicPeriodResponseDto> findOrCreate(Integer year, TermType termType);

    List<AcademicPeriodResponseDto> findActive();

    /** Períodos ANUAL activos cuyo receso (inclusive en ambos extremos) se cruza con [from, to]. */
    List<AcademicPeriodResponseDto> findRecessesBetween(LocalDate from, LocalDate to);

    /** Período ANUAL activo (no eliminado) del año, si existe. */
    Optional<AcademicPeriodResponseDto> findAnnual(int year);

    Page<AcademicPeriodResponseDto> findAll(AcademicPeriodFilter filter, Pageable pageable, boolean includeDeactivated);

    AcademicPeriodResponseDto findById(Long id);

    AcademicPeriodResponseDto update(Long id, UpdateAcademicPeriodRequestDto dto);
}
