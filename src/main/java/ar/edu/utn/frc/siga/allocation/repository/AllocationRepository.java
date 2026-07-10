package ar.edu.utn.frc.siga.allocation.repository;

import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AllocationRepository extends JpaRepository<Allocation, Long> {

    Optional<Allocation> findByOccurrence_Id(Long occurrenceId);

    @Query("SELECT a FROM Allocation a " +
           "JOIN FETCH a.occurrence o " +
           "JOIN FETCH o.event e " +
           "JOIN FETCH a.classroom c " +
           "JOIN FETCH c.building " +
           "WHERE o.date = :date")
    List<Allocation> findByDateEager(@Param("date") LocalDate date);

    @Query("SELECT a FROM Allocation a " +
           "JOIN FETCH a.occurrence o " +
           "JOIN FETCH o.event e " +
           "JOIN FETCH a.classroom c " +
           "JOIN FETCH c.building " +
           "WHERE a.id = :id")
    Optional<Allocation> findByIdEager(@Param("id") Long id);

    /**
     * Ocupación existente en un rango de fechas para un estado de ocurrencia dado: para
     * calcular no-solapamiento. Filtrar por status evita que occurrences CANCELLED/SUSPENDED
     * (cuya allocation no se borra) bloqueen un aula que en realidad está libre.
     */
    @Query("SELECT a FROM Allocation a " +
           "JOIN FETCH a.occurrence o " +
           "JOIN FETCH o.event e " +
           "JOIN FETCH a.classroom c " +
           "WHERE o.date BETWEEN :from AND :to AND o.status = :status")
    List<Allocation> findOccupancyBetween(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                           @Param("status") OccurrenceStatus status);
}
