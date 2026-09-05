package ar.edu.utn.frc.siga.roomrequest.dto.response;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record RoomRequestItemRowDto(
        Long itemId,
        RoomRequestStatus status,
        LocalDateTime decidedAt,
        RoomRequestRowHeaderDto request,
        CommissionResponseDto commission,
        LocalDate date,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Integer classroomCount
) {}
