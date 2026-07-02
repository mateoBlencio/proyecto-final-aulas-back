package ar.edu.utn.frc.siga.academic.model;

import ar.edu.utn.frc.siga.academic.model.Subject;
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
    private Subject subject;

    @ManyToOne
    @JoinColumn(name = "id_comision", nullable = false)
    private Commission commission;

    @Column(name = "cantidad_inscriptos", nullable = false)
    private Integer enrolledCount;

    @Column(name = "cantidad_estimada")
    private Integer estimatedCount;

    @Column(name = "modalidad")
    private String modality;

    @Column(name = "requiere_laboratorio", nullable = false)
    @Builder.Default
    private Boolean requiresLaboratory = false;

    @Column(name = "permite_superposicion", nullable = false)
    @Builder.Default
    private Boolean allowsOverlap = false;

    @Column(name = "eliminado")
    @Builder.Default
    private Boolean deleted = false;
}
