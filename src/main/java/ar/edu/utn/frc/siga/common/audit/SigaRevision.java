package ar.edu.utn.frc.siga.common.audit;

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

/**
 * Entidad de revisión propia de Hibernate Envers.
 *
 * <p>Reemplaza la {@code DefaultRevisionEntity} que trae Envers de fábrica —cuyo timestamp es un
 * {@code long} (epoch millis)— por un campo {@link LocalDateTime}, legible directamente en la
 * tabla {@code revinfo} sin conversión. Envers soporta {@code LocalDateTime} como tipo de
 * {@link RevisionTimestamp} desde Hibernate 6, y este proyecto corre sobre Hibernate ORM 7.
 *
 * <p>Se usa exclusivamente para auditar el módulo {@code allocation} (ver ADR-007): quién y
 * cuándo creó, modificó o eliminó una asignación de aula, ocurrencia o evento académico. Envers
 * la instancia y persiste automáticamente en cada transacción que toca una entidad
 * {@code @Audited}; no se referencia a mano en ningún otro punto del código.
 */
@Entity
@Table(name = "revinfo")
@RevisionEntity
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
}
