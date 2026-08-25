package ar.edu.utn.frc.siga.academic.model;

import ar.edu.utn.frc.siga.common.model.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "materia_comision")
@SQLRestriction("eliminado_en IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class SubjectCommission extends TimestampedEntity {

    @EqualsAndHashCode.Include
    @EmbeddedId
    private SubjectCommissionId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("subjectId")
    @JoinColumn(name = "id_materia", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("commissionId")
    @JoinColumn(name = "id_comision", nullable = false)
    private Commission commission;

    @Column(name = "cantidad_inscriptos", nullable = false)
    private Integer enrolledCount;

    @Column(name = "es_presencial", nullable = false)
    @Builder.Default
    private Boolean inPerson = true;

    @Column(name = "eliminado_en")
    private Instant deletedAt;
}
