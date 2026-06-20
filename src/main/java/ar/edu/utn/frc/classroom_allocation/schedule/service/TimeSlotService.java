package ar.edu.utn.frc.classroom_allocation.schedule.service;

import ar.edu.utn.frc.classroom_allocation.schedule.model.TimeSlot;
import java.time.LocalTime;
import java.util.Optional;

public interface TimeSlotService {

    Optional<TimeSlot> findByDayOfWeekAndStartTimeAndEndTime(
            String dayOfWeek, LocalTime startTime, LocalTime endTime);

    TimeSlot save(TimeSlot timeSlot);
}
