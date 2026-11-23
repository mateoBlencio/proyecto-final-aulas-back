package ar.edu.utn.frc.siga.common.realtime;

/**
 * Traduce el alta, la modificación o la baja de una entidad a un {@link LiveChange}. Lo implementa el módulo dueño
 * de la entidad; el módulo realtime lo invoca después del commit. No debe tocar la base: corre en el callback
 * post-commit de Hibernate.
 */
public interface LiveChangeMapper<T> {

    Class<T> entityType();

    /**
     * @param current  la entidad con su estado actual (el borrado, en una baja)
     * @param previous valores anteriores de una modificación; {@link PreviousState#NONE} en un alta o una baja, y
     *                 {@code null} si Hibernate no conoce el estado anterior
     */
    LiveChange map(T current, PreviousState previous);

    @FunctionalInterface
    interface PreviousState {

        PreviousState NONE = property -> null;

        Object get(String property);
    }
}
