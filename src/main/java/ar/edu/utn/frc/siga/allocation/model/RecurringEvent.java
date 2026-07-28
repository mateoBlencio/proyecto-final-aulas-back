package ar.edu.utn.frc.siga.allocation.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.envers.Audited;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Clase regular que se dicta un día fijo de la semana (cursada) dentro de una ventana de
 * fechas, generando una {@link Occurrence} semanal. {@code subjectId}/{@code commissionId}
 * (referencia a {@code academic} por ID plano) viven en {@link AcademicEvent}, compartidos
 * con {@link UniqueEvent}.
 */
@Entity
@Table(name = "evento_recurrente")
@DiscriminatorValue("RECURRING")
@Audited
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

    @Override
    public EventType getType() {
        return EventType.RECURRING;
    }

    /**
     * Genera una occurrence por cada semana desde {@code startDate} (ajustada al próximo
     * {@code dayOfWeek} igual o posterior) hasta {@code endDate} inclusive (o un año desde
     * {@code startDate} si es null).
     */
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
