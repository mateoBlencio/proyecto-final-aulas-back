package ar.edu.utn.frc.siga.roomrequest.dto.response;

import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;

public record RoomRequestItemStatusCountDto(RoomRequestStatus status, long count) {}
