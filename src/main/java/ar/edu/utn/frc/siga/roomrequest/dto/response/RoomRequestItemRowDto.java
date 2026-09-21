package ar.edu.utn.frc.siga.roomrequest.dto.response;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record RoomRequestItemRowDto(
        Long itemId,
        RoomRequestStatus status,
        LocalDateTime decidedAt,
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
        LocalDateTime derivedAt,
        Boolean wasReturned,
        Integer assignedClassroomCount,
        Boolean partiallyResolved
) {}
