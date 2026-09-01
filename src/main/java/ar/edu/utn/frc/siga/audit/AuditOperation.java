package ar.edu.utn.frc.siga.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.modulith.NamedInterface;

/**
 * Marca un método de servicio como una operación de negocio: todas las revisiones de Envers
 * generadas mientras dura la llamada (incluidas las de transacciones anidadas) comparten un
 * mismo identificador de operación y esta descripción. El registro de auditoría las agrupa en
 * una sola entrada de tipo OPERATION con drill-down a sus cambios individuales.
 *
 * <p>Anidar llamadas anotadas no abre una operación nueva: la más externa gana.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@NamedInterface("api")
public @interface AuditOperation {

    String value();
}
