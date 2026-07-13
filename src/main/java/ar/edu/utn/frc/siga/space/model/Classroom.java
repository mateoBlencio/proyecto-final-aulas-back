package ar.edu.utn.frc.siga.space.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * Aula física asignable a eventos académicos: pertenece a un {@link Building}, tiene
 * capacidad y un {@link ClassroomType}, y puede marcarse {@code available = false}
 * para excluirla de la asignación (manual o automática) sin eliminarla.
 */
@Entity
@Table(name = "aula", uniqueConstraints = @UniqueConstraint(columnNames = {"id_edificio", "num_aula"}))
@SQLRestriction("eliminado = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Classroom {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_edificio", nullable = false)
    private Building building;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_aula")
    private ClassroomType classroomType;

}
