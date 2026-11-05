package ar.edu.utn.frc.siga.roomrequest.event;

import org.springframework.modulith.NamedInterface;

import java.time.Instant;

/** El pedido pasó a RESOLVED: el docente aceptó, o venció el plazo para responder. */
@NamedInterface("api")
public record RoomRequestItemAccepted(Long itemId, String acceptedBy, Instant acceptedAt) {
}
