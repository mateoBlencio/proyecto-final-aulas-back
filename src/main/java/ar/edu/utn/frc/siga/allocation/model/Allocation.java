package ar.edu.utn.frc.siga.allocation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "asignacion_aula")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Allocation {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_asignacion")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ocurrencia")
    private Occurrence occurrence;

    /**
     * ID del aula asignada (space::Classroom). Referencia por ID plano en vez de
     * {@code @ManyToOne}: la FK física sigue en la BD, pero la integridad referencial
     * a nivel de módulo la garantiza solo el esquema, no una relación JPA cross-módulo.
     */
    @Column(name = "id_aula", nullable = false)
    private Integer classroomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen", nullable = false)
    private AllocationSource source;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "observaciones")
    private String observation;
}
