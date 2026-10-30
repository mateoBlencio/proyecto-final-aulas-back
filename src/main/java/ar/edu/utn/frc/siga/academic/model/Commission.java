package ar.edu.utn.frc.siga.academic.model;

import org.hibernate.envers.Audited;
import org.hibernate.envers.AuditOverride;
import org.hibernate.envers.NotAudited;
import ar.edu.utn.frc.siga.common.model.SoftDeletableEntity;
import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Audited
@AuditOverride(forClass = SoftDeletableEntity.class)
@Table(name = "comision",
       uniqueConstraints = @UniqueConstraint(name = "uq_comision_curso_periodo",
               columnNames = {"codigo_curso", "id_periodo_academico"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Commission extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_comision")
    private Long id;

    @Column(name = "codigo_curso", nullable = false)
    private String courseCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_periodo_academico", nullable = false)
    private AcademicPeriod academicPeriod;

    @NotAudited
    @Column(name = "sincronizado_en")
    private Instant syncedAt;

    @NotAudited
    @Column(name = "hash_raw", length = 64)
    private String sysacadHash;

    @Builder.Default
    @Column(name = "habilitado_sysacad", nullable = false)
    private Boolean sysacadEnabled = false;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public boolean isCurrentInSysacad() {
        return Boolean.TRUE.equals(sysacadEnabled) || sysacadHash == null;
    }

    public UnavailableReason unavailableReason() {
        if (!isActive()) {
            return UnavailableReason.DEACTIVATED;
        }
        if (!academicPeriod.isActive()) {
            return UnavailableReason.PARENT_DEACTIVATED;
        }
        return isCurrentInSysacad() ? null : UnavailableReason.NOT_IN_SYSACAD;
    }

    public boolean isAvailable() {
        return unavailableReason() == null;
    }
}
