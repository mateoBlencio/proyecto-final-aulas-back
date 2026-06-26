package ar.edu.utn.frc.classroom_allocation.allocation.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "evento_unico")
@DiscriminatorValue("UNIQUE")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public final class UniqueEvent extends AcademicEvent {

    @Column(name = "fecha", nullable = false)
    private LocalDate date;

    @Column(name = "descripcion")
    private String description;

    @Override
    public List<Occurrence> toOccurrences() {
        return List.of(Occurrence.builder()
                .event(this)
                .date(date)
                .status(OccurrenceStatus.SCHEDULED)
                .build());
    }
}
