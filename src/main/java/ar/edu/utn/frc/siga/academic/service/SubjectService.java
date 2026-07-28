package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import java.util.Collection;
import java.util.List;

import org.springframework.modulith.NamedInterface;

/**
 * Fachada de materias: son datos de catálogo, cargados por fuera de esta app (no crea).
 * Consumida por otros módulos (p. ej. {@code allocation}, {@code excelimport}) sin
 * exponer la entidad JPA.
 */
@NamedInterface("api")
public interface SubjectService {

    List<SubjectResponseDto> findAll();

    SubjectResponseDto findById(Long id);

    List<SubjectResponseDto> findByIds(Collection<Long> ids);

    /**
     * Busca por código dentro de un plan de estudio ({@code studyPlanCode}/{@code specialtyCode}
     * identifican el plan por su clave natural compuesta); lanza {@code ResourceNotFoundException}
     * si la materia, el plan o la especialidad no existen.
     */
    SubjectResponseDto findByCodeAndStudyPlan(Integer code, Integer studyPlanCode, Integer specialtyCode);

    /**
     * Materias de todos los planes de una especialidad (filtro, no valida que la especialidad
     * exista: sin resultados devuelve lista vacía, igual que {@code findAll} sin filtro).
     */
    List<SubjectResponseDto> findBySpecialtyCode(Integer specialtyCode);
}
