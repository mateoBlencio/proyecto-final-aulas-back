package ar.edu.utn.frc.siga.events.repository;

import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/** Acceso a {@code RecurringEvent} (clases regulares que se repiten semanalmente). */
@Repository
public interface RecurringEventRepository extends JpaRepository<RecurringEvent, Long> {

    /**
     * Busca un recurrente ya existente con exactamente los mismos datos identificatorios;
     * respalda {@code findOrCreateRecurringEvent}.
     */
    Optional<RecurringEvent> findBySubjectIdAndCommissionIdAndDayOfWeekAndStartTimeAndStartDateAndEndDate(
            Long subjectId, Long commissionId, DayOfWeek dayOfWeek, LocalTime startTime,
            LocalDate startDate, LocalDate endDate);
}
