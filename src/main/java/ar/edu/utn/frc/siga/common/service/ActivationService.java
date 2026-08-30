package ar.edu.utn.frc.siga.common.service;

/**
 * Contrato uniforme de activación/desactivación (soft-delete) para entidades con borrado lógico.
 *
 * <p>Cada {@code *ServiceImpl} lo implementa delegando en su propio {@code SoftDeletableRepository}
 * ({@code restore} / {@code softDelete} sobre la entidad resuelta con {@code findById} — que ve filas
 * borradas, para poder reactivarlas). Ambas operaciones son idempotentes.
 *
 * @param <ID> tipo del identificador de la entidad (p. ej. {@code Long} o un id compuesto)
 */
public interface ActivationService<ID> {

    /** Reactiva/restaura la entidad. Idempotente: sobre una entidad activa es un no-op. */
    void activate(ID id);

    /** Desactiva (soft-delete) la entidad. Idempotente: conserva el timestamp de la primera baja. */
    void deactivate(ID id);
}
