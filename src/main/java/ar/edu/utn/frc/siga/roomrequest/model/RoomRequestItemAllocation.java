package ar.edu.utn.frc.siga.roomrequest.model;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.common.model.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
       uniqueConstraints = @UniqueConstraint(name = "uq_solicitud_item_asignacion_item_ocurrencia",
                                             columnNames = {"id_item", "id_ocurrencia"}))
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

    /** Número de aula del pedido (1..classroomCount), no de ocurrencia: un REGULAR_ROOM_CHANGE repite el mismo valor en varias filas, una por clase futura. */
    @Column(name = "orden", nullable = false)
    private Integer position;

    /** Aula y origen de la clase antes del primer assign del pedido. Nulos si la ocurrencia no tenía aula o si el pedido es anterior a V60. */
    @Column(name = "id_aula_original")
    private Long originalClassroomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen_original", length = 20)
    private AllocationSource originalSource;

    /** False en las filas anteriores a V60; en las demás, un {@code originalClassroomId} nulo significa que la clase no tenía aula. */
    @Column(name = "original_capturado", nullable = false)
    private boolean originalCaptured;

    public record Original(Long classroomId, AllocationSource source) {}

    void moveTo(Long classroomId) {
        this.classroomId = classroomId;
    }

    void update(Long classroomId, Integer position) {
        this.classroomId = classroomId;
        this.position = position;
    }
}
