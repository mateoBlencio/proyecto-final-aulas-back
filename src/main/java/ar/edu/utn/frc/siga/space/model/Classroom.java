package ar.edu.utn.frc.siga.space.model;

import ar.edu.utn.frc.siga.common.model.RecordSource;
import ar.edu.utn.frc.siga.common.model.TimestampedEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "aula", uniqueConstraints = @UniqueConstraint(columnNames = {"id_edificio", "num_aula"}))
@SQLRestriction("eliminado = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Classroom extends TimestampedEntity {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_aula")
    private Integer id;

    @Column(name = "num_aula", nullable = false, length = 20)
    private String roomNumber;

    @Column(name = "piso")
    private Integer floor;

    @Column(name = "capacidad")
    private Integer capacity;

    @Builder.Default
    @Column(name = "disponible", nullable = false)
    private Boolean available = true;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_edificio", nullable = false)
    private Building building;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_aula")
    private ClassroomType classroomType;

}
