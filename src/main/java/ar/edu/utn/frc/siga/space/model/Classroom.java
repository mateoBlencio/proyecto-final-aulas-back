package ar.edu.utn.frc.siga.space.model;

import org.hibernate.envers.Audited;
import org.hibernate.envers.AuditOverride;
import org.hibernate.envers.NotAudited;
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
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Audited
@AuditOverride(forClass = SoftDeletableEntity.class)
@Table(name = "aula")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Classroom extends SoftDeletableEntity {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_aula")
    private Long id;

    @Column(name = "numero", nullable = false)
    private Integer roomNumber;

    @Column(name = "capacidad", nullable = false)
    private Integer capacity;

    /** Sysacad's "habilitada" (S/N) flag. Written only from a view row, never because of absence. */
    @Builder.Default
    @Column(name = "raw_habilitada", nullable = false)
    private Boolean sysacadEnabled = false;

    /** {@code false} when the classroom stopped appearing in the Sysacad view. Does not remove it from the candidates. */
    @Builder.Default
    @Column(name = "vigente_sysacad", nullable = false)
    private Boolean sysacadCurrent = true;

    @NotAudited
    @Column(name = "sincronizado_en")
    private Instant syncedAt;

    @NotAudited
    @Column(name = "hash_raw", length = 64)
    private String sysacadHash;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_edificio", nullable = false)
    private Building building;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_aula", nullable = false)
    private ClassroomType classroomType;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "modo_permiso", nullable = false, length = 20)
    private PermissionMode permissionMode = PermissionMode.ALL;

    @Column(name = "observaciones", length = 500)
    private String observations;

    public boolean isCurrentInSysacad() {
        return Boolean.TRUE.equals(sysacadCurrent) || sysacadHash == null;
    }

    /** Only "habilitada = N" excludes the classroom; manually created ones (null hash) do not depend on the flag. */
    public boolean isEnabledInSysacad() {
        return Boolean.TRUE.equals(sysacadEnabled) || sysacadHash == null;
    }

    public UnavailableReason unavailableReason() {
        if (!isActive()) {
            return UnavailableReason.DEACTIVATED;
        }
        if (!building.isActive()) {
            return UnavailableReason.PARENT_DEACTIVATED;
        }
        if (!building.isCurrentInSysacad()) {
            return UnavailableReason.NOT_IN_SYSACAD;
        }
        return isEnabledInSysacad() ? null : UnavailableReason.DISABLED_IN_SYSACAD;
    }

    public boolean isAvailable() {
        return unavailableReason() == null;
    }
}
