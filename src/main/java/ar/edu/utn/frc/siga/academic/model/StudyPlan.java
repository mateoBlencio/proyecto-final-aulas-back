package ar.edu.utn.frc.siga.academic.model;

import org.hibernate.envers.Audited;
import org.hibernate.envers.AuditOverride;
import org.hibernate.envers.NotAudited;
import ar.edu.utn.frc.siga.common.model.SoftDeletableEntity;
import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Audited
@AuditOverride(forClass = SoftDeletableEntity.class)
@Table(name = "plan_estudio",
       uniqueConstraints = @UniqueConstraint(columnNames = {"codigo_plan", "id_especialidad"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudyPlan extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_plan")
    private Long id;

    @Column(name = "codigo_plan", nullable = false)
    private Integer planCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_especialidad", nullable = false)
    private Specialty specialty;

    @NotAudited
    @Column(name = "sincronizado_en")
    private Instant syncedAt;

    @NotAudited
    @Column(name = "hash_sysacad", length = 64)
    private String sysacadHash;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public UnavailableReason unavailableReason() {
        if (!isActive()) {
            return UnavailableReason.DEACTIVATED;
        }
        return specialty.isActive() ? null : UnavailableReason.PARENT_DEACTIVATED;
    }

    public boolean isAvailable() {
        return unavailableReason() == null;
    }
}
