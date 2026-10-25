package ar.edu.utn.frc.siga.space.model;

import org.hibernate.envers.Audited;
import org.hibernate.envers.AuditOverride;
import org.hibernate.envers.NotAudited;
import ar.edu.utn.frc.siga.common.model.SoftDeletableEntity;
import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Audited
@AuditOverride(forClass = SoftDeletableEntity.class)
@Table(name = "edificio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Building extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_edificio")
    private Long id;

    @Column(name = "codigo_edificio", unique = true)
    private Integer buildingCode;

    @Column(name = "nombre", nullable = false, length = 100)
    private String name;

    @NotAudited
    @Column(name = "sincronizado_en")
    private Instant syncedAt;

    @NotAudited
    @Column(name = "hash_sysacad", length = 64)
    private String sysacadHash;

    @Builder.Default
    @Column(name = "habilitado_sysacad", nullable = false)
    private Boolean sysacadEnabled = true;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @NotAudited
    @JsonIgnore
    @OneToMany(mappedBy = "building", fetch = FetchType.LAZY)
    private List<Classroom> classrooms;

    public boolean isCurrentInSysacad() {
        return Boolean.TRUE.equals(sysacadEnabled) || sysacadHash == null;
    }

    public UnavailableReason unavailableReason() {
        if (!isActive()) {
            return UnavailableReason.DEACTIVATED;
        }
        return isCurrentInSysacad() ? null : UnavailableReason.NOT_IN_SYSACAD;
    }

    public boolean isAvailable() {
        return unavailableReason() == null;
    }
}
