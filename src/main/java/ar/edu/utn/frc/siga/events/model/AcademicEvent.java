package ar.edu.utn.frc.siga.events.model;

import ar.edu.utn.frc.siga.common.converter.DurationMinutesConverter;
import ar.edu.utn.frc.siga.common.model.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

@Entity
@Table(name = "evento_academico")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "tipo_evento")
@Audited
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public abstract class AcademicEvent extends TimestampedEntity {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "evento_academico_seq")
    @SequenceGenerator(name = "evento_academico_seq", sequenceName = "evento_academico_id_evento_academico_seq", allocationSize = 1)
    @Column(name = "id_evento_academico")
    protected Long id;

    @Setter
    @Column(name = "cantidad_inscriptos", nullable = false)
    protected Integer enrolled;

    @Setter
    @Column(name = "hora_inicio", nullable = false)
    protected LocalTime startTime;

    @Setter
    @Convert(converter = DurationMinutesConverter.class)
    @Column(name = "duracion_minutos", nullable = false)
    protected Duration duration;

    @Setter
    @Column(name = "id_materia")
    protected Long subjectId;

    @Setter
    @Column(name = "id_comision")
    protected Long commissionId;

    @Setter
    @NotAudited
    @Column(name = "sincronizado_en")
    protected Instant syncedAt;

    @Setter
    @NotAudited
    @Column(name = "hash_sysacad", length = 64)
    protected String sysacadHash;

    @Setter
    @Builder.Default
    @NotAudited
    @Column(name = "habilitado_sysacad", nullable = false)
    protected Boolean sysacadEnabled = false;

    public LocalTime endTime() {
        return startTime.plus(duration);
    }

    public abstract EventType getType();

    public abstract List<Occurrence> toOccurrences();
}
