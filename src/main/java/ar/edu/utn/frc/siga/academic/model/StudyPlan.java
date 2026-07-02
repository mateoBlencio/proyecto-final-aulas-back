package ar.edu.utn.frc.siga.academic.model;

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
@Table(name = "plan_estudio",
       uniqueConstraints = @UniqueConstraint(columnNames = {"codigo_plan", "id_especialidad"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudyPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_plan")
    private Long id;

    @Column(name = "codigo_plan", nullable = false)
    private Integer planCode;

    @ManyToOne
    @JoinColumn(name = "id_especialidad", nullable = false)
    private Specialty specialty;

    @Column(name = "eliminado")
    @Builder.Default
    private Boolean deleted = false;
}
