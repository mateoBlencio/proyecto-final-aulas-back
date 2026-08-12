package ar.edu.utn.frc.siga.events.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.util.List;

/**
 * Evento académico que ocurre una sola vez (mesa de examen final, parcial, trabajo
 * práctico, o una mesa especial fuera de calendario). Genera exactamente una {@link Occurrence}.
 * {@code subjectId}/{@code commissionId} viven en {@link AcademicEvent}, compartidos con
 * {@link RecurringEvent}.
 */
@Entity
@Table(name = "evento_unico_academico")
@DiscriminatorValue("UNIQUE_EVENT")
@Audited
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public final class UniqueEvent extends AcademicEvent {

    @Setter
    @Column(name = "fecha", nullable = false)
    private LocalDate date;

    @Setter
    @Column(name = "descripcion")
    private String description;

    /** Tipo de actividad: Parcial, Trabajo Práctico, Examen final u Otro. */
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_actividad", nullable = false)
    private UniqueEventKind kind;

    @Override
    public EventType getType() {
        return EventType.UNIQUE_EVENT;
    }

    @Override
    public List<Occurrence> toOccurrences() {
        return List.of(Occurrence.builder()
                .event(this)
                .date(date)
                .status(OccurrenceStatus.NEEDS_ROOM)
                .build());
    }
}
