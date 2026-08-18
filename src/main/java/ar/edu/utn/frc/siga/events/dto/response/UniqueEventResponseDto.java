package ar.edu.utn.frc.siga.events.dto.response;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import org.springframework.modulith.NamedInterface;

import java.time.LocalDate;
import java.time.LocalTime;

@NamedInterface("api")
public record UniqueEventResponseDto(
        Long id,
        EventType type,
        UniqueEventKind eventType,
        Integer enrolled,
        LocalTime startTime,
        long durationMinutes,
        LocalDate date,
        String description,
        SubjectResponseDto subject,
        CommissionResponseDto commission
) implements AcademicEventResponseDto {
}
