package ar.edu.utn.frc.siga.roomrequest.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ClassDateSlotsDto(LocalDate date, List<TimeSlot> slots) {

    public record TimeSlot(
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime
    ) {}
}
