package ar.edu.utn.frc.siga.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@MappedSuperclass
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class SoftDeletableEntity extends TimestampedEntity implements SoftDeletable {

    @Column(name = "eliminado_en")
    private Instant deletedAt;

    @Override
    public boolean isActive() {
        return deletedAt == null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    @Override
    public void deactivate(Instant when) {
        if (deletedAt == null) {
            deletedAt = when;
        }
    }

    public void deactivate() {
        deactivate(Instant.now());
    }

    @Override
    public void activate() {
        deletedAt = null;
    }
}
