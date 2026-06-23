package ar.edu.utn.frc.classroom_allocation.allocation.model;

import ar.edu.utn.frc.classroom_allocation.common.converter.DurationMinutesConverter;
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
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

@Entity
@Table(name = "evento_academico")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "tipo_evento")
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

    @Column(name = "inscritos", nullable = false)
    protected Integer enrolled;

    @Column(name = "hora_inicio", nullable = false)
    protected LocalTime startTime;

    @Convert(converter = DurationMinutesConverter.class)
    @Column(name = "duracion_minutos", nullable = false)
    protected Duration duration;

    @Transient
    protected String planningId;

    public LocalTime endTime() {
        return startTime.plus(duration);
    }

    public abstract List<Occurrence> toOccurrences();
}
