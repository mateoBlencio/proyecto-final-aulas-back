package ar.edu.utn.frc.siga.common.service;

import java.util.Collection;
import java.util.Set;

/**
 * Puerto para que un módulo que no depende de {@code allocation} pregunte si una ocurrencia tiene
 * asignación, sin invertir la dependencia de Modulith (la implementación vive en {@code allocation};
 * el consumidor la inyecta por esta interfaz de {@code common}). La existencia de la fila de
 * asignación es la fuente de verdad: si hay fila, la ocurrencia está asignada.
 */
public interface OccurrenceAllocationQuery {

    Set<Long> allocatedAmong(Collection<Long> occurrenceIds);
}
