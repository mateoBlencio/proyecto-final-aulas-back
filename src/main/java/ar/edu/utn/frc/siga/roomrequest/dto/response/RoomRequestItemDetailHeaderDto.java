package ar.edu.utn.frc.siga.roomrequest.dto.response;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;

import java.time.Instant;

public record RoomRequestItemDetailHeaderDto(
        Long id,
        RoomRequestType type,
        AcademicScope scope,
        String teacherName,
        String teacherEmail,
        String teacherPhone,
        SubjectResponseDto subject,
        Instant createdAt
) {}
