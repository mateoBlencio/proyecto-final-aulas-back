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
     * Período(s) en vigencia hoy (año + cuatrimestre en curso). Lectura pura: no re-significa
     * {@link #findActive()} ni materializa nada. Si el año en curso todavía no tiene filas, ver
     * {@link #materializeCurrentYear()}.
     */
    List<AcademicPeriodResponseDto> findCurrent();

    /**
     * Comando explícito de materialización: si el año en curso no tiene filas, las genera desde el
     * año previo (rollover perezoso, fechas ajustadas a la semana). Idempotente y tolerante a la
     * carrera contra el índice único (anio, cuatrimestre).
     */
    void materializeCurrentYear();

    List<AcademicPeriodResponseDto> findAll(boolean includeDeactivated);

    AcademicPeriodResponseDto findById(Long id);

    AcademicPeriodResponseDto update(Long id, UpdateAcademicPeriodRequestDto dto);
}
