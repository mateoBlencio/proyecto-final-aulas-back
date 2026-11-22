package ar.edu.utn.frc.siga.space.dto.response;

import org.springframework.modulith.NamedInterface;

import java.util.List;

@NamedInterface("api")
public record AffectedRoomRequestDto(
        Long itemId,
        Long requestId,
        String status,
        String teacherName,
        String subjectName,
        List<Long> classroomIds
) {}
