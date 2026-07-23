package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import java.util.Collection;
import java.util.List;

import org.springframework.modulith.NamedInterface;

/**
 * Fachada de comisiones: resolución por ID y por clave natural, sin exponer la entidad
 * JPA. Comisión es catálogo cargado por fuera de esta app: no se crea desde acá.
 */
@NamedInterface("api")
public interface CommissionService {

    CommissionResponseDto findById(Long id);

    List<CommissionResponseDto> findByIds(Collection<Long> ids);

    List<CommissionResponseDto> findAll();

    /** {@code periodYear}/{@code periodSemester} identifican el período por su clave natural. */
    CommissionResponseDto findByCourseAndNumberAndPeriod(String courseCode, Integer commissionNumber,
            Integer periodYear, Integer periodSemester);
}
