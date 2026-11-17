package ar.edu.utn.frc.siga.common.service;

import java.util.Collection;
import java.util.Set;

/**
 * Puerto para que {@code events} sepa qué ocurrencias quedaron referenciadas por un pedido de aula sin
 * depender de {@code roomrequest} (que depende de {@code events}). La fila del pedido se conserva aunque
 * la ocurrencia ya no tenga aula (pedido cancelado, aula liberada), y su FK impide borrar la ocurrencia.
 */
public interface OccurrenceRequestReferenceQuery {

    Set<Long> referencedAmong(Collection<Long> occurrenceIds);
}
