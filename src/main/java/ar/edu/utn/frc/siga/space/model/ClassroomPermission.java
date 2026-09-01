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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "aula_permiso",
       uniqueConstraints = @UniqueConstraint(name = "uq_aula_permiso",
               columnNames = {"id_aula", "tipo_objetivo", "id_objetivo"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassroomPermission extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_aula_permiso")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_aula", nullable = false)
    private Classroom classroom;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_objetivo", nullable = false, length = 20)
    private PermissionTargetKind targetKind;

    @Column(name = "id_objetivo", nullable = false)
    private Long targetId;
}
