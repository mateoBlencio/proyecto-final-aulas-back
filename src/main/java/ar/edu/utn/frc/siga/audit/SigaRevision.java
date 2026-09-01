package ar.edu.utn.frc.siga.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
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
}
