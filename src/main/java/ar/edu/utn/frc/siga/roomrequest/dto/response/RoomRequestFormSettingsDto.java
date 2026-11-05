package ar.edu.utn.frc.siga.roomrequest.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;

/** Valores configurables que el formulario público y la bandeja necesitan conocer. */
public record RoomRequestFormSettingsDto(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime hoursStart,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime hoursEnd,
        int maxClassroomCount,
        int maxEstimated,
        int maxComputerCount,
        int alertDaysBeforeDate
) {}
