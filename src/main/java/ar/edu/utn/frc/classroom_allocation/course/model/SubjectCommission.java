package ar.edu.utn.frc.classroom_allocation.course.model;

import ar.edu.utn.frc.classroom_allocation.career.model.Subject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "materia_comision",
       uniqueConstraints = @UniqueConstraint(columnNames = {"id_materia", "id_comision"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubjectCommission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_materia_comision")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_materia", nullable = false)
    private Subject materia;

    @ManyToOne
    @JoinColumn(name = "id_comision", nullable = false)
    private Commission comision;

    @Column(name = "cantidad_inscriptos", nullable = false)
    private Integer cantidadInscriptos;

    @Column(name = "cantidad_estimada")
    private Integer cantidadEstimada;

    @Column(name = "modalidad")
    private String modalidad;

    @Column(name = "requiere_laboratorio")
    @Builder.Default
    private Boolean requiereLaboratorio = false;

    @Column(name = "permite_superposicion")
    @Builder.Default
    private Boolean permiteSuperposicion = false;

    @Column(name = "eliminado")
    @Builder.Default
    private Boolean deleted = false;
}
