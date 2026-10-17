package ar.edu.utn.frc.siga.buildingactivity.model;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Log inmutable de movimientos de aula de un edificio; {@code lines} es un JSON de {@link ActivityLine}. */
@Entity
@Table(name = "actividad_edificio")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BuildingActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_actividad_edificio")
    private Long id;

    @Column(name = "id_edificio", nullable = false)
    private Long buildingId;

    @Column(name = "nombre_edificio", nullable = false, length = 150)
    private String buildingName;

    @Column(name = "operacion_id", nullable = false, length = 36)
    private String operationId;

    @Column(name = "usuario", nullable = false)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen", nullable = false, length = 20)
    private AllocationSource source;

    @Column(name = "motivo", length = 500)
    private String reason;

    @Column(name = "ocurrido_en", nullable = false)
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "lineas", nullable = false, columnDefinition = "jsonb")
    private String lines;
}
