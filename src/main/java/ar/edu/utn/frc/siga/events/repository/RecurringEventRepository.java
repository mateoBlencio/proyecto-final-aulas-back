package ar.edu.utn.frc.siga.events.repository;

import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * "Sync-owned": sólo {@link ar.edu.utn.frc.siga.events.service.AcademicEventService#syncRecurringEvent}
     * setea {@code sysacadHash} — un evento cargado a mano o por Excel nunca lo tiene, así que este
     * filtro aísla exactamente los eventos que el sync de SysAcad puede marcar ausentes.
     */
    List<RecurringEvent> findBySysacadHashIsNotNull();
}
