package ar.edu.utn.frc.siga.roomrequest.model;

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
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RoomPreference {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_preferencia")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_item", nullable = false)
    private RoomRequestItem item;

    @Column(name = "id_aula", nullable = false)
    private Integer classroomId;

    @Column(name = "orden", nullable = false)
    private Integer position;
}
