package ar.edu.utn.frc.siga.roomrequest.model;

import org.springframework.modulith.NamedInterface;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@NamedInterface("api")
public record RoomRequestResolved(
        Long itemId,
        Long requestId,
        String teacherName,
        String teacherEmail,
        Long subjectId,
        LocalDate date,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        List<Long> classroomIds
) {}
