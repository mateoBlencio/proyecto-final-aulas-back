package ar.edu.utn.frc.siga.space.model;

import ar.edu.utn.frc.siga.common.model.RecordSource;
import ar.edu.utn.frc.siga.common.model.TimestampedEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "edificio")
@SQLRestriction("eliminado = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Building extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_edificio")
    private Integer id;

    @Column(name = "codigo_edificio", unique = true)
    private Integer buildingCode;

    @Column(name = "nombre", nullable = false, length = 100)
    private String name;

    @Column(name = "cantidad_pisos")
    private Integer floorCount;

    @Builder.Default
    @Column(name = "activo")
    private Boolean active = true;

    @Builder.Default
    @Column(name = "eliminado", nullable = false)
    private Boolean deleted = false;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "origen", nullable = false, length = 16)
    private RecordSource source = RecordSource.LOCAL;

    @Column(name = "sincronizado_en")
    private Instant syncedAt;

    @Column(name = "hash_sysacad", length = 64)
    private String sysacadHash;

    @Builder.Default
    @Column(name = "vigente_sysacad", nullable = false)
    private Boolean presentInSysacad = true;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @JsonIgnore
    @OneToMany(mappedBy = "building", fetch = FetchType.LAZY)
    private List<Classroom> classrooms;

}
