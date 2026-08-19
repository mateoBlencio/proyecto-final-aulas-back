package ar.edu.utn.frc.siga.settings.model;

import ar.edu.utn.frc.siga.common.model.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "configuracion")
@Audited
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Setting extends TimestampedEntity {

    @EqualsAndHashCode.Include
    @Id
    @Column(name = "clave")
    private String key;

    @Setter
    @Column(name = "valor", nullable = false)
    private String value;

    public Setting(String key, String value) {
        this.key = key;
        this.value = value;
    }
}
