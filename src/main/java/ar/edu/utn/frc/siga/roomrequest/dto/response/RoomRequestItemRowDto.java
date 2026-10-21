package ar.edu.utn.frc.siga.roomrequest.dto.response;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestAccessControl;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record RoomRequestItemRowDto(
        Long itemId,
        RoomRequestStatus status,
        Instant decidedAt,
        RoomRequestRowHeaderDto request,
        List<CommissionResponseDto> commissions,
        LocalDate date,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Integer classroomCount,
        Boolean requiresComputers,
        Boolean requiresSpecialAssignment,
        Long derivedBuildingId,
        String derivedBuildingName,
        Instant derivedAt,
        Boolean wasReturned,
        Integer assignedClassroomCount,
        Boolean partiallyResolved,
        @Schema(description = "Acciones que el usuario autenticado puede ejecutar sobre el ítem según rol, estado y edificio derivado. Pista de UI: el back valida igual en cada endpoint.")
        List<RoomRequestAccessControl.Action> allowedActions
) {}
