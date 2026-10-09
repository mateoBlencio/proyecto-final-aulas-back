package ar.edu.utn.frc.siga.roomrequest.dto.response;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record RoomRequestItemResponseDto(
        Long id,
        Integer position,
        RoomRequestStatus status,
        String decidedBy,
        Instant decidedAt,
        String decisionReason,
        List<CommissionResponseDto> commissions,
        LocalDate date,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Long durationMinutes,
        Integer estimated,
        Integer enrolled,
        Integer classroomCount,
        Boolean requiresProjector,
        Boolean requiresComputers,
        Integer computerCount,
        Boolean requiresExamUsers,
        String requiredSoftware,
        String observations,
        List<ClassroomOptionDto> preferredClassrooms,
        List<ClassroomOptionDto> currentClassrooms,
        List<AssignedClassroomDto> assignedClassrooms,
        AssignedClassroomDto previousClassroom,
        Instant notifiedAt,
        BuildingOptionDto derivedBuilding,
        Instant derivedAt,
        BuildingOptionDto returnedFromBuilding,
        String returnedReason
) {}
