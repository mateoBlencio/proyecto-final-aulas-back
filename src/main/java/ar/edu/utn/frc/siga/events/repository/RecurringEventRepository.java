package ar.edu.utn.frc.siga.events.repository;

import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecurringEventRepository extends JpaRepository<RecurringEvent, Long> {

    Optional<RecurringEvent> findBySubjectIdAndCommissionIdAndDayOfWeekAndStartTimeAndStartDateAndEndDate(
            Long subjectId, Long commissionId, DayOfWeek dayOfWeek, LocalTime startTime,
            LocalDate startDate, LocalDate endDate);

    /**
     * Cursado vigente de una comisión en una materia: eventos que SysAcad tiene activos
     * ({@code sysacadEnabled}) o que se cargaron a mano ({@code sysacadHash} nulo, nunca los toca el sync),
     * cuyo período de dictado no terminó todavía. Es la fuente de "días y horarios de cursado" para las
     * solicitudes de aula (cambio de aula y parcial en horario de clases).
     */
    @Query("""
            select r from RecurringEvent r
            where r.subjectId = :subjectId and r.commissionId = :commissionId
              and (r.sysacadEnabled = true or r.sysacadHash is null)
              and (r.endDate is null or r.endDate >= :onOrAfter)
            order by r.dayOfWeek, r.startTime
            """)
    List<RecurringEvent> findActiveBySubjectAndCommission(@Param("subjectId") Long subjectId,
                                                          @Param("commissionId") Long commissionId,
                                                          @Param("onOrAfter") LocalDate onOrAfter);

    /**
     * "Sync-owned": sólo {@link ar.edu.utn.frc.siga.events.service.AcademicEventService#syncRecurringEvent}
     * setea {@code sysacadHash} — un evento cargado a mano o por Excel nunca lo tiene, así que este
     * filtro aísla exactamente los eventos que el sync de SysAcad puede marcar ausentes.
     */
    List<RecurringEvent> findBySysacadHashIsNotNull();
}
