package ar.edu.utn.frc.siga.events.dto.request;

import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.modulith.NamedInterface;

import java.time.LocalDate;
import java.time.LocalTime;

@NamedInterface("api")
public record UpdateUniqueEventRequestDto(
        @NotNull UniqueEventKind eventType,
        Long subjectId,
        Long commissionId,
        @NotNull LocalDate date,
        @NotNull LocalTime startTime,
        @Min(1) int durationMinutes,
        @NotNull @Min(1) Integer enrolled,
        String description
) {}
