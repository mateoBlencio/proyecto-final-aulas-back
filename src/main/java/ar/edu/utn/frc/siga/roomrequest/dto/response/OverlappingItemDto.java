package ar.edu.utn.frc.siga.roomrequest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;

public record OverlappingItemDto(
        Long itemId,
        String subjectName,
        @Schema(description = "Código de la comisión; null cuando el otro pedido es para todas las comisiones de la materia.")
        String commissionCode,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime
) {}
