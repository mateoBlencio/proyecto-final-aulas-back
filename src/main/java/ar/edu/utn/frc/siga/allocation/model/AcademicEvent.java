package ar.edu.utn.frc.siga.allocation.model;

import ar.edu.utn.frc.siga.common.converter.DurationMinutesConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.envers.Audited;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

/**
 * Evento académico base (recurrente o único). Auditada con Hibernate Envers (ver ADR-007), junto
 * con sus subtipos {@link RecurringEvent} y {@link UniqueEvent}: los cambios quedan registrados
 * en {@code evento_academico_aud} y sus tablas {@code _aud} de subclase (herencia JOINED).
 */
@Entity
@Table(name = "evento_academico")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "tipo_evento")
@Audited
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public abstract class AcademicEvent {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evento_academico")
    protected Long id;

    @Column(name = "cantidad_inscriptos", nullable = false)
    protected Integer enrolled;

    @Column(name = "hora_inicio", nullable = false)
    protected LocalTime startTime;

    @Convert(converter = DurationMinutesConverter.class)
    @Column(name = "duracion_minutos", nullable = false)
    protected Duration duration;

    /** Hora de fin derivada: {@code startTime + duration}. El horario vive en el evento, no en la occurrence. */
    public LocalTime endTime() {
        return startTime.plus(duration);
    }

    /** Tipo concreto del evento (espejo del discriminador de herencia); la subclase es la fuente de verdad. */
    public abstract EventType getType();

    /**
     * Genera todas las {@link Occurrence} de este evento (una vez, no bajo demanda), cada
     * una nacida en estado {@code SCHEDULED} sin aula asignada.
     */
    public abstract List<Occurrence> toOccurrences();
}
