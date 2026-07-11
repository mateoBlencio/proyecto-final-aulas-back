package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationProblemsResponseDto;

import java.time.LocalDate;

/**
 * Detección de problemas de asignación de aulas para la pantalla de asignación
 * automática: eventos sin aula, aulas con sobrecupo y superposiciones de horario-aula.
 */
public interface AllocationProblemService {

    /**
     * Busca los tres listados de problemas en el rango [from, to]. {@code from} nulo
     * default a hoy; {@code to} nulo default al fin del período académico activo (o
     * {@code from + 6 meses} si no hay período activo con {@code endDate}).
     */
    AllocationProblemsResponseDto findProblems(LocalDate from, LocalDate to);
}
