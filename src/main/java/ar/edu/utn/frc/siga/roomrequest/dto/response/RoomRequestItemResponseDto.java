package ar.edu.utn.frc.siga.roomrequest.dto.response;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record RoomRequestItemResponseDto(
        Long id,
        Integer position,
        RoomRequestStatus status,
        String decidedBy,
        LocalDateTime decidedAt,
        String decisionReason,
        List<CommissionResponseDto> commissions,
        LocalDate date,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Long durationMinutes,
        Integer estimated,
        Integer classroomCount,
        Boolean requiresProjector,
        Boolean requiresComputers,
        Integer computerCount,
        Boolean requiresExamUsers,
        String requiredSoftware,
        String observations,
        List<ClassroomOptionDto> preferredClassrooms,
        List<AssignedClassroomDto> assignedClassrooms,
        LocalDateTime notifiedAt,
        BuildingOptionDto derivedBuilding,
        LocalDateTime derivedAt,
        BuildingOptionDto returnedFromBuilding,
        String returnedReason
) {}
