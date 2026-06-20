package ar.edu.utn.frc.classroom_allocation.course.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "periodo_academico",
       uniqueConstraints = @UniqueConstraint(columnNames = {"anio", "cuatrimestre"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_periodo_academico")
    private Long id;

    @Column(name = "anio", nullable = false)
    private Integer anio;

    @Column(name = "cuatrimestre", nullable = false)
    private Integer cuatrimestre;

    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Column(name = "activo")
    @Builder.Default
    private Boolean activo = true;
}
