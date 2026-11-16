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
}
