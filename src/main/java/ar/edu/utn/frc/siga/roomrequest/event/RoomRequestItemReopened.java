package ar.edu.utn.frc.siga.roomrequest.event;

import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import org.springframework.modulith.NamedInterface;

import java.time.Instant;

/** El pedido volvió a IN_EVALUATION desde NOTIFIED o RESOLVED. */
@NamedInterface("api")
public record RoomRequestItemReopened(Long itemId, RoomRequestStatus previousStatus, String reason, String reopenedBy,
                                      Instant reopenedAt) {
}
