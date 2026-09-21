package ar.edu.utn.frc.siga.roomrequest.dto.request;

import jakarta.validation.constraints.NotNull;

public record DeriveRoomRequestItemDto(@NotNull Long buildingId) {}
