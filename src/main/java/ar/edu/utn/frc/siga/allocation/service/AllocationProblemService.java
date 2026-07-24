package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ClassroomOverlapDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OvercrowdedAllocationDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

/**
 * Detección de problemas de asignación de aulas para la pantalla de asignación
 * automática, cada uno en su propio listado: eventos sin aula, aulas con sobrecupo
 * y superposiciones de horario-aula.
 *
 * <p>Los tres comparten la misma resolución de rango: {@code from} nulo default a hoy;
 * {@code to} nulo default al fin del período académico activo (o {@code from + 6 meses}
 * si no hay período activo con {@code endDate}); {@code to} anterior a {@code from}
 * lanza {@code InvalidDateRangeException}.
 */
public interface AllocationProblemService {

    /**
     * Eventos con ocurrencias SCHEDULED (sin aula) en el rango. Excluye ocurrencias ya
     * pasadas salvo que {@code includePast} sea true.
     */
    Page<AcademicEventResponseDto> findUnassigned(LocalDate from, LocalDate to, boolean includePast, Pageable pageable);

    /**
     * Pares evento-aula donde los inscriptos superan la capacidad del aula asignada.
     * Excluye ocurrencias ya pasadas salvo que {@code includePast} sea true.
     */
    Page<OvercrowdedAllocationDto> findOvercrowded(LocalDate from, LocalDate to, boolean includePast, Pageable pageable);

    /**
     * Pares de eventos cuyos horarios se superponen en la misma aula. Excluye
     * ocurrencias ya pasadas salvo que {@code includePast} sea true.
     */
    Page<ClassroomOverlapDto> findOverlaps(LocalDate from, LocalDate to, boolean includePast, Pageable pageable);
}
