package ar.edu.utn.frc.classroom_allocation.course.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "comision")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Commission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_comision")
    private Long id;

    @Column(name = "codigo_curso", nullable = false)
    private String codigoCurso;

    @Column(name = "numero_comision", nullable = false)
    private Integer numeroComision;

    @Column(name = "anio_nivel")
    private Integer anioNivel;

    @ManyToOne
    @JoinColumn(name = "id_periodo", nullable = false)
    private AcademicPeriod periodo;

    @Column(name = "eliminado")
    @Builder.Default
    private Boolean deleted = false;
}
