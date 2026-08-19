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

/**
 * Un aula que el docente preferiría, con su orden de prioridad dentro del ítem.
 *
 * <p>Es una preferencia declarada: nadie la valida contra disponibilidad ni
 * capacidad. Subsecretaría la ve al procesar la solicitud y decide.
 */
@Entity
@Table(name = "solicitud_aula_preferencia",
       uniqueConstraints = {
           @UniqueConstraint(name = "uq_solicitud_preferencia_item_aula",
                             columnNames = {"id_item", "id_aula"}),
           @UniqueConstraint(name = "uq_solicitud_preferencia_item_orden",
                             columnNames = {"id_item", "orden"})
       })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class RoomPreference extends TimestampedEntity {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_preferencia")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_item", nullable = false)
    private RoomRequestItem item;

    /** ID plano: el aula vive en {@code space}. */
    @Column(name = "id_aula", nullable = false)
    private Integer classroomId;

    /** Prioridad: 1 es la primera opción. */
    @Column(name = "orden", nullable = false)
    private Integer position;
}
