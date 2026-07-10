package ar.edu.utn.frc.siga.allocation.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "evento_unico_academico")
@DiscriminatorValue("UNIQUE_EVENT")
@Audited
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public final class UniqueEvent extends AcademicEvent {

    @Column(name = "fecha", nullable = false)
    private LocalDate date;

    @Column(name = "descripcion")
    private String description;

    @Override
    public EventType getType() {
        return EventType.UNIQUE_EVENT;
    }

    @Override
    public List<Occurrence> toOccurrences() {
        return List.of(Occurrence.builder()
                .event(this)
                .date(date)
                .status(OccurrenceStatus.SCHEDULED)
                .build());
    }
}
