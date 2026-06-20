package ar.edu.utn.frc.classroom_allocation.career.model;

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
@Table(name = "materia",
       uniqueConstraints = @UniqueConstraint(columnNames = {"codigo_materia", "id_plan"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_materia")
    private Long id;

    @Column(name = "codigo_materia", nullable = false)
    private Integer code;

    @Column(name = "nombre", nullable = false)
    private String name;

    @ManyToOne
    @JoinColumn(name = "id_plan", nullable = false)
    private StudyPlan studyPlan;

    @Column(name = "dictado")
    private String term;

    @Column(name = "eliminado")
    @Builder.Default
    private Boolean deleted = false;
}
