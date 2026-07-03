package ar.edu.utn.frc.siga.space.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
@Entity
@Table(name = "aula", uniqueConstraints = @UniqueConstraint(columnNames = {"id_edificio", "num_aula"}))
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
