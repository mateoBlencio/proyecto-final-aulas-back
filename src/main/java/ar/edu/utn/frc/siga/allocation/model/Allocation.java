package ar.edu.utn.frc.siga.allocation.model;

import ar.edu.utn.frc.siga.common.model.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "asignacion_aula")
@Audited
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Allocation extends TimestampedEntity {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "asignacion_aula_seq")
    @SequenceGenerator(name = "asignacion_aula_seq", sequenceName = "asignacion_aula_id_asignacion_seq", allocationSize = 1)
    @Column(name = "id_asignacion")
    private Long id;

    @Column(name = "id_ocurrencia", nullable = false)
    private Long occurrenceId;

    @Column(name = "id_aula", nullable = false)
    private Long classroomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen", nullable = false)
    private AllocationSource source;

    @Column(name = "observaciones")
    private String observation;
}
