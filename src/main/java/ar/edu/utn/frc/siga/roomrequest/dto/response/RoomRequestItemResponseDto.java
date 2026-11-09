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
        Instant acceptanceDeadline,
        Instant acceptedAt,
        String acceptedBy,
        BuildingOptionDto derivedBuilding,
        Instant derivedAt,
        BuildingOptionDto returnedFromBuilding,
        String returnedReason,
        List<ReleasedDateDto> releasedTargetDates,
        ReversionDto reversion
) {

    /** Solo lo llena la reapertura; el composer deja {@code reversion} nulo. Estático para que MapStruct no lo tome por setter. */
    public static RoomRequestItemResponseDto withReversion(RoomRequestItemResponseDto base, ReversionDto reversion) {
        return new RoomRequestItemResponseDto(base.id, base.position, base.status, base.decidedBy, base.decidedAt,
                base.decisionReason, base.commissions, base.date, base.dayOfWeek, base.startTime, base.endTime,
                base.durationMinutes, base.estimated, base.enrolled, base.classroomCount, base.requiresProjector,
                base.requiresComputers, base.computerCount, base.requiresExamUsers, base.requiredSoftware,
                base.observations, base.preferredClassrooms, base.currentClassrooms, base.assignedClassrooms,
                base.previousClassroom, base.notifiedAt, base.acceptanceDeadline, base.acceptedAt, base.acceptedBy,
                base.derivedBuilding, base.derivedAt, base.returnedFromBuilding, base.returnedReason,
                base.releasedTargetDates, reversion);
    }
}
