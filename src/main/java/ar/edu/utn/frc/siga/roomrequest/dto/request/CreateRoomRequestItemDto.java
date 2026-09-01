package ar.edu.utn.frc.siga.roomrequest.dto.request;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public sealed interface CreateRoomRequestItemDto permits ScheduledItemDto, FreeFormItemDto {

    default Long commissionId() {
        return null;
    }

    default LocalDate date() {
        return null;
    }

    default DayOfWeek dayOfWeek() {
        return null;
    }

    default LocalTime startTime() {
        return null;
    }

    default LocalTime endTime() {
        return null;
    }

    default Integer estimated() {
        return null;
    }

    Integer classroomCount();

    default Boolean requiresProjector() {
        return null;
    }

    default Boolean requiresComputers() {
        return null;
    }

    default Integer computerCount() {
        return null;
    }

    default Boolean requiresExamUsers() {
        return null;
    }

    default String requiredSoftware() {
        return null;
    }

    default String observations() {
        return null;
    }

    List<Long> preferredClassroomIds();

    default Duration duration() {
        return (startTime() == null || endTime() == null) ? null : Duration.between(startTime(), endTime());
    }
}
