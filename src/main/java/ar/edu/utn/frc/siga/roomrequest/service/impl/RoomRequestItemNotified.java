package ar.edu.utn.frc.siga.roomrequest.service.impl;

/** Un pedido pasó a NOTIFIED; el mail al docente sale después del commit. {@code round} es la ronda que lo selló. */
record RoomRequestItemNotified(Long itemId, int round) {
}
