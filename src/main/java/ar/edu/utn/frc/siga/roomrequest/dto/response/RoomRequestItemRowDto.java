package ar.edu.utn.frc.siga.roomrequest.dto.response;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;

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
        Boolean partiallyResolved
) {}
