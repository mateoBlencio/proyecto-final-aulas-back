package ar.edu.utn.frc.siga.academic.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
@Entity
@Table(name = "comision", uniqueConstraints = @UniqueConstraint(columnNames = {"id_periodo", "codigo_curso", "numero_comision"}))
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

    @ManyToOne
    @JoinColumn(name = "id_periodo", nullable = false)
    private AcademicPeriod academicPeriod;

    @Column(name = "eliminado")
    @Builder.Default
    private Boolean deleted = false;
}
