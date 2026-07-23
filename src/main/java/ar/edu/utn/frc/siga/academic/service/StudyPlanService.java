package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.StudyPlanResponseDto;

import java.util.List;

import org.springframework.modulith.NamedInterface;

/** Fachada de planes de estudio: son datos de catálogo, cargados por fuera de esta app (no crea). */
@NamedInterface("api")
public interface StudyPlanService {

    List<StudyPlanResponseDto> findAll();

    StudyPlanResponseDto findById(Long id);

    /**
     * Busca por código de plan dentro de una especialidad (clave natural compuesta);
     * lanza {@code ResourceNotFoundException} si el plan o la especialidad no existen.
     */
    StudyPlanResponseDto findByPlanCodeAndSpecialtyCode(Integer planCode, Integer specialtyCode);
}
