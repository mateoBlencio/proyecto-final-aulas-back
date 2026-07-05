package ar.edu.utn.frc.siga.allocation.model;

import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.Commission;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecurringEvent extends AcademicEvent {

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate startDate;

    @Column(name = "fecha_fin")
    private LocalDate endDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_materia")
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_comision")
    private Commission commission;

    @ElementCollection
    @CollectionTable(name = "evento_recurrente_fecha_excluida", joinColumns = @JoinColumn(name = "id_evento_academico"))
    @Column(name = "fecha")
    List<LocalDate> excludedDates;

    @Override
    public List<Occurrence> toOccurrences() {
        List<Occurrence> result = new ArrayList<>();
        LocalDate end = endDate != null ? endDate : startDate.plusYears(1);
        LocalDate current = startDate.with(TemporalAdjusters.nextOrSame(dayOfWeek));
        List<LocalDate> excluded = excludedDates != null ? excludedDates : List.of();
        while (!current.isAfter(end)) {
            if (!excluded.contains(current)) {
                result.add(Occurrence.builder()
                        .event(this)
                        .date(current)
                        .status(OccurrenceStatus.SCHEDULED)
                        .build());
            }
            current = current.plusWeeks(1);
        }
        return result;
    }
}
