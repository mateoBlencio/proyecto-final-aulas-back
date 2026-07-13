package ar.edu.utn.frc.siga.allocation.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Clase regular que se dicta un día fijo de la semana (cursada) dentro de una ventana de
 * fechas, generando una {@link Occurrence} semanal. Referencia materia y comisión
 * ({@code academic}) por ID plano, sin relación JPA cross-módulo.
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

    /** ID de la materia (academic::Subject). Referencia por ID plano, sin relación JPA cross-módulo. */
    @Column(name = "id_materia")
    private Long subjectId;

    /** ID de la comisión (academic::Commission). Referencia por ID plano, sin relación JPA cross-módulo. */
    @Column(name = "id_comision")
    private Long commissionId;

    /**
     * OBSOLETO: nunca se escribe (solo se lee en {@link #toOccurrences()}, siempre vacío en la
     * práctica). Se marca {@code @NotAudited} para no generar una tabla de auditoría
     * ({@code evento_recurrente_fecha_excluida_aud}) para un campo que jamás cambia.
     */
    @ElementCollection
    @CollectionTable(name = "evento_recurrente_fecha_excluida", joinColumns = @JoinColumn(name = "id_evento_academico"))
    @Column(name = "fecha")
    @NotAudited
    List<LocalDate> excludedDates;

    @Override
    public EventType getType() {
        return EventType.RECURRING;
    }

    /**
     * Genera una occurrence por cada semana desde {@code startDate} (ajustada al próximo
     * {@code dayOfWeek} igual o posterior) hasta {@code endDate} inclusive (o un año desde
     * {@code startDate} si es null), salteando las {@code excludedDates}.
     */
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
