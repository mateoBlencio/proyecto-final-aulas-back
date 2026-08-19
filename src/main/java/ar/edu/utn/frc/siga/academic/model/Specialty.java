package ar.edu.utn.frc.siga.academic.model;

import ar.edu.utn.frc.siga.common.model.RecordSource;
import ar.edu.utn.frc.siga.common.model.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "especialidad")
@SQLRestriction("eliminado = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Specialty extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_especialidad")
    private Long id;

    @Column(name = "codigo_especialidad", unique = true, nullable = false)
    private Integer specialtyCode;

    @Column(name = "nombre")
    private String name;

    @Column(name = "eliminado")
    @Builder.Default
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
}
