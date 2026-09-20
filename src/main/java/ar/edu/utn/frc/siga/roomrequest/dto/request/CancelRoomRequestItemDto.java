package ar.edu.utn.frc.siga.roomrequest.dto.request;

import jakarta.validation.constraints.Size;

public record CancelRoomRequestItemDto(@Size(max = 255) String reason) {}
