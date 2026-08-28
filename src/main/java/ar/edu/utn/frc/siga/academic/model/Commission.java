package ar.edu.utn.frc.siga.academic.model;

import ar.edu.utn.frc.siga.common.model.TimestampedEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "comision")
@SQLRestriction("eliminado_en IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Commission extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_comision")
    private Long id;

    @Column(name = "codigo_curso", nullable = false)
    private String courseCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_periodo_academico", nullable = false)
    private AcademicPeriod academicPeriod;

    @Column(name = "eliminado_en")
    private Instant deletedAt;

    @Column(name = "sincronizado_en")
    private Instant syncedAt;

    @Column(name = "hash_sysacad", length = 64)
    private String sysacadHash;

    @Builder.Default
    @Column(name = "habilitado_sysacad", nullable = false)
    private Boolean sysacadEnabled = false;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
