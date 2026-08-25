package ar.edu.utn.frc.siga.preview.dto.response;

import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;

import java.time.LocalDate;

public record RoomStretchDto(
        ClassroomResponseDto classroom,
        LocalDate from,
        LocalDate to,
        int classes
) {
}
