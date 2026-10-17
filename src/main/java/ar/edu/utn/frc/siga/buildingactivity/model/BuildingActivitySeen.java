package ar.edu.utn.frc.siga.buildingactivity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Marca de agua por usuario: lo mayor que vio. Se escribe solo por upsert nativo. */
@Entity
@Table(name = "actividad_edificio_visto")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BuildingActivitySeen {

    @Id
    @Column(name = "usuario")
    private String user;

    @Column(name = "id_ultima_actividad", nullable = false)
    private Long lastSeenActivityId;
}
