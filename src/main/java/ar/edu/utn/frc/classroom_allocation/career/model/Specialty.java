package ar.edu.utn.frc.classroom_allocation.career.model;

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
@Table(name = "especialidad")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Specialty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_especialidad")
    private Long id;

    @Column(name = "codigo_especialidad", unique = true, nullable = false)
    private Integer specialtyCode;

    @Column(name = "nombre", nullable = false)
    private String name;

    @Column(name = "eliminado")
    @Builder.Default
    private Boolean deleted = false;
}
