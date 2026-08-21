package ar.edu.utn.frc.siga.allocation.model;

/**
 * De dónde viene el bloqueo de una ocurrencia en la vista previa de impacto. Separa los dos casos
 * porque se resuelven distinto: uno se destraba moviendo algo que ya está asignado, el otro se
 * destraba corrigiendo el pedido antes de mandarlo.
 */
public enum BlockerKind {

    /** El aula ya está ocupada por una asignación existente. Se puede correr esa clase de aula. */
    EXISTING_ALLOCATION,

    /** Dos items del mismo pedido se pisan entre sí. No hay nada escrito: hay que corregir el lote. */
    SAME_BATCH
}
