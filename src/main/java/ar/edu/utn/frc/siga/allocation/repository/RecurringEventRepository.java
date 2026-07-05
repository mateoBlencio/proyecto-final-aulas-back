package ar.edu.utn.frc.siga.allocation.repository;

import ar.edu.utn.frc.siga.allocation.model.RecurringEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

@Repository
public interface RecurringEventRepository extends JpaRepository<RecurringEvent, Long> {

    Optional<RecurringEvent> findBySubject_IdAndCommission_IdAndDayOfWeekAndStartTimeAndStartDateAndEndDate(
            Long subjectId, Long commissionId, DayOfWeek dayOfWeek, LocalTime startTime,
            LocalDate startDate, LocalDate endDate);
}
