package ar.edu.utn.frc.siga.space.model;

import ar.edu.utn.frc.siga.common.model.SoftDeletableEntity;
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

    @Builder.Default
    @Column(name = "habilitada_sysacad", nullable = false)
    private Boolean sysacadEnabled = false;

    @Column(name = "sincronizado_en")
    private Instant syncedAt;

    @Column(name = "hash_sysacad", length = 64)
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

}
