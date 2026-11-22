package ar.edu.utn.frc.siga.audit;

import org.springframework.modulith.NamedInterface;

/**
 * Evento que lleva el identificador de la operación auditada que lo provocó. Si un método
 * anotado con {@link AuditOperation} recibe un {@code AuditCause} como argumento, la operación
 * que abre queda registrada como hija de {@link #originOperationId()}.
 *
 * <p>El id viaja dentro del evento (y no en un ThreadLocal) porque Modulith persiste el evento
 * y lo reintenta en otro hilo, incluso después de un reinicio.
 */
@NamedInterface("api")
public interface AuditCause {

    /** Id de la operación que publicó el evento, o null si se publicó fuera de una operación. */
    String originOperationId();
}
