package ar.edu.utn.frc.siga.allocation.repository;

import ar.edu.utn.frc.siga.allocation.model.Allocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AllocationRepository extends JpaRepository<Allocation, Long>, AllocationRepositoryCustom {

    Optional<Allocation> findByOccurrenceId(Long occurrenceId);

    @Query(value = """
            SELECT a.* FROM asignacion_aula a
            JOIN ocurrencia o ON o.id_ocurrencia = a.id_ocurrencia
            WHERE a.id_aula IN (:classroomIds) AND o.fecha >= :from
            """, nativeQuery = true)
    List<Allocation> findByClassroomIdInFromDate(@Param("classroomIds") Collection<Long> classroomIds,
                                                 @Param("from") LocalDate from);

    @Query(value = """
            select distinct o.id_evento_academico
            from ocurrencia o
            where o.fecha between :from and :to
              and (o.estado = 'NEEDS_ROOM' or o.motivo_liberacion = 'CLASSROOM_DEACTIVATED')
              and not exists (select 1 from asignacion_aula a where a.id_ocurrencia = o.id_ocurrencia)
              and not exists (select 1 from evento_academico_suspendido s
                              where s.id_evento_academico = o.id_evento_academico and o.fecha >= s.fecha_baja)
            """, nativeQuery = true)
    List<Long> findEventIdsWithUnallocatedOccurrencesBetween(LocalDate from, LocalDate to);

    @Query(value = """
            select distinct a.id_ocurrencia
            from asignacion_aula a
            join ocurrencia o on o.id_ocurrencia = a.id_ocurrencia
            join unnest(cast(:classroomIds as bigint[]), cast(:dates as date[])) as k(id_aula, fecha)
              on k.id_aula = a.id_aula and k.fecha = o.fecha
            where not exists (select 1 from evento_academico_suspendido s
                              where s.id_evento_academico = o.id_evento_academico and o.fecha >= s.fecha_baja)
            """, nativeQuery = true)
    List<Long> findOccurrenceIdsAllocatedAt(Long[] classroomIds, LocalDate[] dates);
}
