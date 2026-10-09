package ar.edu.utn.frc.siga.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One archive run that moved rows. The archive writes by JDBC, which Envers does not see; this audited
 * record is what leaves a trace in the audit log.
 */
@Entity
@Table(name = "archivado_auditoria")
@Audited
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditArchiveRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_archivado")
    private Long id;

    @Column(name = "fecha_ejecucion", nullable = false)
    private LocalDateTime executedAt;

    @Column(name = "ciclo_conservado_desde", nullable = false)
    private int retainedFromCycle;

    @Column(name = "fecha_corte", nullable = false)
    private LocalDate cutoff;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30)
    private AuditArchiveStatus status;

    @Column(name = "filas_ocurrencia", nullable = false)
    private long occurrenceRows;

    @Column(name = "filas_asignacion", nullable = false)
    private long allocationRows;

    @Column(name = "revisiones_borradas", nullable = false)
    private long deletedRevisions;

    @Column(name = "mensaje_error", length = 255)
    private String errorMessage;

    public AuditArchiveRun(LocalDateTime executedAt, int retainedFromCycle, LocalDate cutoff) {
        this.executedAt = executedAt;
        this.retainedFromCycle = retainedFromCycle;
        this.cutoff = cutoff;
        this.status = AuditArchiveStatus.IN_PROGRESS;
    }
}
