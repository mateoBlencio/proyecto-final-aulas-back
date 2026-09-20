package ar.edu.utn.frc.siga.roomrequest.model;

import ar.edu.utn.frc.siga.common.model.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;

@Entity
@Audited
@Table(name = "solicitud_item_asignacion",
       uniqueConstraints = {
           @UniqueConstraint(name = "uq_solicitud_item_asignacion_item_aula",
                             columnNames = {"id_item", "id_aula"}),
           @UniqueConstraint(name = "uq_solicitud_item_asignacion_item_orden",
                             columnNames = {"id_item", "orden"})
       })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class RoomRequestItemAllocation extends TimestampedEntity {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_item_asignacion")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_item", nullable = false)
    private RoomRequestItem item;

    @Column(name = "id_ocurrencia", nullable = false)
    private Long occurrenceId;

    @Column(name = "id_aula", nullable = false)
    private Long classroomId;

    @Column(name = "orden", nullable = false)
    private Integer position;
}
