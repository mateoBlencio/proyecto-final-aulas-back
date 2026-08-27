package ar.edu.utn.frc.siga.roomrequest.dto.response;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record RoomRequestItemRowDto(
        Long itemId,
        Integer position,
        RoomRequestStatus status,
        String decidedBy,
        LocalDateTime decidedAt,
        String decisionReason,
        RoomRequestRowHeaderDto request,
        CommissionResponseDto commission,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        Long durationMinutes,
        Integer enrolled,
        Integer estimated,
        Integer classroomCount,
        ClassroomOptionDto currentClassroom,
        Boolean requiresProjector,
        Boolean requiresComputers,
        Integer computerCount,
        Boolean requiresExamUsers,
        String requiredSoftware,
        String observations,
        List<ClassroomOptionDto> preferredClassrooms
) {}
