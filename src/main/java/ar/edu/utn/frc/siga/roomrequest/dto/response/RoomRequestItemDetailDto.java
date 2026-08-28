package ar.edu.utn.frc.siga.roomrequest.dto.response;

public record RoomRequestItemDetailDto(
        RoomRequestItemDetailHeaderDto request,
        RoomRequestItemResponseDto item
) {}
