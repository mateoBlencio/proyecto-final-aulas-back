package ar.edu.utn.frc.siga.roomrequest.dto.request;

import jakarta.validation.constraints.Size;

import java.util.List;

public record AssignRoomRequestItemDto(List<Long> classroomIds, @Size(max = 255) String reason) {}
