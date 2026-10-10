package ar.edu.utn.frc.siga.dashboard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Closed-month snapshot of one building (or the campus when {@code buildingId} is null). Immutable:
 * only {@code current} changes, when a regeneration retires the row.
 */
@Entity
@Table(name = "tablero_snapshot")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DashboardSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_snapshot")
    private Long id;

    @Column(name = "anio", nullable = false)
    private int year;

    @Column(name = "mes", nullable = false)
    private int month;

    @Column(name = "id_edificio")
    private Long buildingId;

    @Builder.Default
    @Column(name = "vigente", nullable = false)
    private boolean current = true;

    @Column(name = "version_esquema", nullable = false)
    private int schemaVersion;

    @Column(name = "version_reglas", nullable = false)
    private int rulesVersion;

    @Column(name = "generado_en", nullable = false)
    private Instant generatedAt;

    @Column(name = "generado_por", nullable = false, length = 150)
    private String generatedBy;

    @Column(name = "motivo_regeneracion", length = 500)
    private String regenerationReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos", nullable = false, columnDefinition = "jsonb")
    private String payload;
}
