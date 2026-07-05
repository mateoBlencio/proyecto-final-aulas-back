package ar.edu.utn.frc.siga.space.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tipo_aula")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassroomType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_aula")
    private Integer id;

    @Column(name = "descripcion", nullable = false, length = 50)
    private String description;

    @Builder.Default
    @Column(name = "eliminado", nullable = false)
    private Boolean deleted = false;
}
