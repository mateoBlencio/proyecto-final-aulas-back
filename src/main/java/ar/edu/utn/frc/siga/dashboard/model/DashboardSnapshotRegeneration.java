package ar.edu.utn.frc.siga.dashboard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;

/**
 * One manual regeneration of a month. Snapshot rows are not audited; this record leaves the trace in the
 * audit log. Who asked for it is in the revision ({@code revinfo.usuario}).
 */
@Entity
@Table(name = "regeneracion_tablero")
@Audited
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DashboardSnapshotRegeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_regeneracion")
    private Long id;

    @Column(name = "anio", nullable = false)
    private int year;

    @Column(name = "mes", nullable = false)
    private int month;

    @Column(name = "motivo", nullable = false, length = 500)
    private String reason;

    @Column(name = "regenerado_en", nullable = false)
    private Instant regeneratedAt;

    public DashboardSnapshotRegeneration(int year, int month, String reason, Instant regeneratedAt) {
        this.year = year;
        this.month = month;
        this.reason = reason;
        this.regeneratedAt = regeneratedAt;
    }
}
