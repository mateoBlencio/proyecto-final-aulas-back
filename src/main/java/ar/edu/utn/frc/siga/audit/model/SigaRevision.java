package ar.edu.utn.frc.siga.audit.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import ar.edu.utn.frc.siga.audit.internal.SigaRevisionListener;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

@Entity
@Table(name = "revinfo")
@RevisionEntity(SigaRevisionListener.class)
@Getter
@Setter
public class SigaRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    @Column(name = "rev")
    private Integer id;

    @RevisionTimestamp
    @Column(name = "fecha_revision", nullable = false)
    private LocalDateTime fechaRevision;

    @Column(name = "usuario")
    private String usuario;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @Column(name = "operacion_id", length = 36)
    private String operacionId;

    @Column(name = "operacion_padre_id", length = 36)
    private String parentOperationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_actor", nullable = false, length = 10)
    private ActorType actorType;

    /**
     * Rows changed in this revision per root {@code _aud} table and revision type (table {@code revinfo_resumen}).
     * {@code SigaRevisionListener.entityChanged} accumulates it in memory; Hibernate writes it once at flush.
     * Writes by direct SQL do not feed it.
     */
    @ElementCollection
    @CollectionTable(name = "revinfo_resumen", joinColumns = @JoinColumn(name = "rev"))
    @Column(name = "cantidad", nullable = false)
    private Map<RevisionSummaryKey, Integer> summary = new HashMap<>();
}
