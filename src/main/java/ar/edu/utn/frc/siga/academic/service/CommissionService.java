package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import java.util.Collection;
import java.util.List;

import org.springframework.modulith.NamedInterface;

/**
 * Fachada de comisiones: resolución por ID y find-or-create idempotente por clave
 * natural (curso + número de comisión + período), sin exponer la entidad JPA.
 */
@NamedInterface("api")
public interface CommissionService {

    CommissionResponseDto findById(Long id);

    List<CommissionResponseDto> findByIds(Collection<Long> ids);

    /** {@code periodYear}/{@code periodSemester} identifican el período por su clave natural. */
    FindOrCreateResult<CommissionResponseDto> findOrCreate(String courseCode, Integer commissionNumber,
            Integer yearLevel, Integer periodYear, Integer periodSemester);
}
