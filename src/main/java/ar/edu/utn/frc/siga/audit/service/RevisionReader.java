package ar.edu.utn.frc.siga.audit.service;

import ar.edu.utn.frc.siga.audit.dto.response.RevisionDto;
import org.springframework.modulith.NamedInterface;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Lectura del historial de Envers para las entidades auditadas. Cada método devuelve las
 * revisiones ordenadas por número de revisión ascendente.
 */
@NamedInterface("api")
public interface RevisionReader {

    <E, S> List<RevisionDto<S>> read(Class<E> entityClass, String property, Object value, Function<E, S> toSnapshot);

    <E, S> List<RevisionDto<S>> readById(Class<E> entityClass, Object id, Function<E, S> toSnapshot);

    <E, S> List<RevisionDto<S>> read(Class<E> entityClass, String property, Collection<?> values, Function<E, S> toSnapshot);
}
