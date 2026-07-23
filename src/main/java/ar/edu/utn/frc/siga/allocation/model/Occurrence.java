package ar.edu.utn.frc.siga.allocation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AccessLevel;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Ocurrencia concreta (fecha) de un {@link AcademicEvent}. Auditada con Hibernate Envers:
 * los cambios de estado quedan registrados en {@code ocurrencia_aud}.
 */
@Entity
@Table(name = "ocurrencia")
@Audited
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Occurrence {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ocurrencia_seq")
    @SequenceGenerator(name = "ocurrencia_seq", sequenceName = "ocurrencia_id_ocurrencia_seq", allocationSize = 50)
    @Column(name = "id_ocurrencia")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_evento_academico", nullable = false)
    private AcademicEvent event;

    @Column(name = "fecha", nullable = false)
    private LocalDate date;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private OccurrenceStatus status;

    public LocalTime startTime() {
        return event.getStartTime();
    }

    public LocalTime endTime() {
        return event.endTime();
    }

    /** true si ya pasó el momento de inicio (fecha + hora de inicio del evento vs. ahora). */
    public boolean isPast() {
        return LocalDateTime.now().isAfter(date.atTime(event.getStartTime()));
    }
}
