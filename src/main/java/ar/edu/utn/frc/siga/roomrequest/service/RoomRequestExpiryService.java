package ar.edu.utn.frc.siga.roomrequest.service;

public interface RoomRequestExpiryService {

    /** Cancela los pedidos sin cerrar cuya fecha ya pasó. Devuelve cuántos canceló. */
    int expireOverdueItems();
}
