package ar.edu.utn.frc.siga.academic.model;

import ar.edu.utn.frc.siga.common.model.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "especialidad")
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

    @Column(name = "abreviatura")
    private String abbreviation;

    @Column(name = "sincronizado_en")
    private Instant syncedAt;

    @Column(name = "hash_sysacad", length = 64)
    private String sysacadHash;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
