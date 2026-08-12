package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationConflictDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.NamedInterface;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Detección de problemas de asignación de aulas para la pantalla de asignación
 * automática: eventos sin aula, aulas con sobrecupo y superposiciones de horario-aula,
 * mezclados en un único listado paginado filtrable por tipo. {@code resolveAllUnassignedEventIds}
 * también lo consume {@code preview} para el "seleccionar todos los eventos sin aula".
 *
 * <p>Comparten la misma resolución de rango: {@code from} nulo default a hoy; {@code to} nulo
 * default al fin del período académico activo (o {@code from + 6 meses} si no hay período activo
 * con {@code endDate}); {@code to} anterior a {@code from} lanza {@code InvalidDateRangeException}.
 */
@NamedInterface("api")
public interface AllocationConflictService {

    /**
     * Los conflictos pedidos en {@code types} (los tres si es null/vacío), en el rango
     * (mismos defaults que antes: from=hoy, to=fin del período académico activo o +6 meses),
     * excluyendo pasados salvo includePast=true. Los tres se siguen calculando por separado
     * internamente (algoritmos distintos) y se mezclan+paginan en memoria antes de devolver.
     */
    Page<AllocationConflictDto> findConflicts(Set<ConflictType> types, LocalDate from, LocalDate to,
                                               boolean includePast, Pageable pageable);

    /**
     * IDs de todos los eventos sin aula, con el mismo rango por defecto que
     * {@link #findConflicts} sin parámetros (hoy hasta fin del período académico activo,
     * excluyendo pasadas). Pensado para resolver una selección masiva ("seleccionar todas")
     * sin depender de la paginación del listado.
     */
    List<Long> resolveAllUnassignedEventIds();
}
