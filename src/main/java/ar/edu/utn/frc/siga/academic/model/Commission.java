package ar.edu.utn.frc.siga.academic.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * Comisión de cursado (grupo de alumnos) dentro de un {@link AcademicPeriod}, identificada
 * por su código de curso y número de comisión (p. ej. "1K1").
 */
@Entity
@Table(name = "comision", uniqueConstraints = @UniqueConstraint(columnNames = {"id_periodo", "codigo_curso", "numero_comision"}))
@SQLRestriction("eliminado = false")
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
    private String courseCode;

    @Column(name = "numero_comision")
    private Integer commissionNumber;

    @Column(name = "anio_nivel")
    private Integer yearLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_periodo", nullable = false)
    private AcademicPeriod academicPeriod;

    @Column(name = "eliminado")
    @Builder.Default
    private Boolean deleted = false;
}
