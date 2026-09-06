package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.request.UpdateAcademicPeriodRequestDto;
import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.common.service.ActivationService;
import ar.edu.utn.frc.siga.academic.model.TermType;

import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface AcademicPeriodService extends ActivationService<Long> {

    FindOrCreateResult<AcademicPeriodResponseDto> findOrCreate(Integer year, TermType termType);

    List<AcademicPeriodResponseDto> findActive();

    /**
     * Período(s) vigente(s) hoy (por año + cuatrimestre en curso). Si el año en curso todavía no tiene
     * filas porque el anterior ya terminó, las materializa copiando las del año previo ajustadas a la
     * semana (rollover perezoso). No re-significa {@link #findActive()}, que sigue devolviendo todos
     * los activos.
     */
    List<AcademicPeriodResponseDto> findVigente();

    List<AcademicPeriodResponseDto> findAll(boolean includeDeactivated);

    AcademicPeriodResponseDto findById(Long id);

    AcademicPeriodResponseDto update(Long id, UpdateAcademicPeriodRequestDto dto);
}
