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

@Entity
@Table(name = "evento_unico")
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
    @Column(name = "descripcion", length = 1000)
    private String description;

    @Setter
    @Column(name = "requiere_proyector", nullable = false)
    private boolean requiresProjector;

    @Setter
    @Column(name = "requiere_computadoras", nullable = false)
    private boolean requiresComputers;

    @Setter
    @Column(name = "cantidad_computadoras")
    private Integer computerCount;

    @Setter
    @Column(name = "software_requerido")
    private String requiredSoftware;

    @Setter
    @Column(name = "requiere_usuarios_examen")
    private Boolean requiresExamUsers;

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
