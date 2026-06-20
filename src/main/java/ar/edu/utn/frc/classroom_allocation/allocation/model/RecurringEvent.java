package ar.edu.utn.frc.classroom_allocation.allocation.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "evento_recurrente")
@DiscriminatorValue("RECURRING")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RecurringEvent extends AcademicEvent {

    @Column(name = "dia_semana", nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate startDate;

    @Column(name = "fecha_fin")
    private LocalDate endDate;

    @Column(name = "materia")
    private String subject;

    @Column(name = "comision")
    private String section;

    @Override
    public List<Occurrence> toOccurrences() {
        List<Occurrence> result = new ArrayList<>();
        LocalDate end = endDate != null ? endDate : startDate.plusYears(1);
        LocalDate current = startDate.with(TemporalAdjusters.nextOrSame(dayOfWeek));
        while (!current.isAfter(end)) {
            result.add(Occurrence.builder()
                    .event(this)
                    .date(current)
                    .status(OccurrenceStatus.SCHEDULED)
                    .build());
            current = current.plusWeeks(1);
        }
        return result;
    }
}
