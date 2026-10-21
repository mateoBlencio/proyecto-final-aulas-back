package ar.edu.utn.frc.siga.allocation.dto.response;

import java.time.LocalDate;
import java.util.List;

import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;

/** Ocurrencias liberadas de un evento con el mismo motivo, nota y aula anterior. */
public record ReleasedOccurrenceItemDto(
        AcademicEventResponseDto event,
        ReleaseReason reason,
        String note,
        ClassroomResponseDto previousClassroom,
        List<ReleasedDate> dates
) {
    public record ReleasedDate(Long occurrenceId, LocalDate date) {}
}
