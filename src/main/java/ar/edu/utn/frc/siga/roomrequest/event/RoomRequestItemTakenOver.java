package ar.edu.utn.frc.siga.roomrequest.event;

import org.springframework.modulith.NamedInterface;

import java.time.Instant;

/** Subsecretaría retomó un pedido derivado; {@code buildingId} es el edificio que lo perdió. */
@NamedInterface("api")
public record RoomRequestItemTakenOver(String operationId, Long itemId, Long buildingId, String buildingName,
                                       String status, String teacherName, String reason, String actor,
                                       Instant occurredAt) {
}
