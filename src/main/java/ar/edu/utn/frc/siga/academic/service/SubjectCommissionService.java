package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;

import java.util.List;

import org.springframework.modulith.NamedInterface;

/**
 * Fachada de la relación materia-comisión (cuántos inscriptos tiene una materia dictada
 * en una comisión dada): catálogo cargado por fuera de esta app, no se crea desde acá.
 */
@NamedInterface("api")
public interface SubjectCommissionService {

    SubjectCommissionResponseDto findBySubjectAndCommission(Long subjectId, Long commissionId);

    List<SubjectCommissionResponseDto> findAll();

    SubjectCommissionResponseDto findById(Long id);

    /**
     * Comisiones vinculadas a una materia (filtro, no valida que la materia exista: sin
     * resultados devuelve lista vacía, igual que {@code findAll} sin filtro).
     */
    List<SubjectCommissionResponseDto> findBySubjectId(Long subjectId);
}
