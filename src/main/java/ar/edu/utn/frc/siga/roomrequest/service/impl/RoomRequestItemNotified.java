package ar.edu.utn.frc.siga.roomrequest.service.impl;

/**
 * Un pedido pasó a NOTIFIED; el mail al docente sale después del commit. {@code round} es la ronda que lo selló y
 * {@code actor} el email de quien ejecutó la acción (va en copia del mail).
 */
record RoomRequestItemNotified(Long itemId, int round, String actor) {
}
