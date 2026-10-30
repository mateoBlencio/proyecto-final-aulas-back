package ar.edu.utn.frc.siga.academic.model;

import ar.edu.utn.frc.siga.common.model.SoftDeletableEntity;
import ar.edu.utn.frc.siga.common.model.UnavailableReason;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.AuditOverride;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

@Entity
@Audited
@AuditOverride(forClass = SoftDeletableEntity.class)
@Table(name = "materia_comision",
       uniqueConstraints = @UniqueConstraint(name = "uq_materia_comision",
               columnNames = {"id_materia", "id_comision"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class SubjectCommission extends SoftDeletableEntity {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_materia_comision")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_materia", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_comision", nullable = false)
    private Commission commission;

    @NotAudited
    @Column(name = "cantidad_inscriptos", nullable = false)
    private Integer enrolledCount;

    @Column(name = "es_presencial", nullable = false)
    @Builder.Default
    private Boolean inPerson = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_baja", length = 40)
    private SubjectCommissionDeactivationReason deactivationReason;

    public SubjectCommissionId key() {
        return new SubjectCommissionId(subject.getId(), commission.getId());
    }

    public void deactivateFor(SubjectCommissionDeactivationReason reason, Instant when) {
        deactivate(when);
        deactivationReason = reason;
    }

    @Override
    public void activate() {
        super.activate();
        deactivationReason = null;
    }

    public boolean isDeactivatedFor(SubjectCommissionDeactivationReason reason) {
        return isDeleted() && deactivationReason == reason;
    }

    public UnavailableReason unavailableReason() {
        if (!isActive()) {
            return UnavailableReason.DEACTIVATED;
        }
        if (!subject.isAvailable()) {
            return UnavailableReason.PARENT_DEACTIVATED;
        }
        UnavailableReason commissionReason = commission.unavailableReason();
        if (commissionReason == null) {
            return null;
        }
        return commissionReason == UnavailableReason.NOT_IN_SYSACAD
                ? UnavailableReason.NOT_IN_SYSACAD : UnavailableReason.PARENT_DEACTIVATED;
    }

    public boolean isAvailable() {
        return unavailableReason() == null;
    }
}
