package ar.edu.utn.frc.classroom_allocation.schedule.service.impl;

import ar.edu.utn.frc.classroom_allocation.schedule.model.TimeSlot;
import ar.edu.utn.frc.classroom_allocation.schedule.repository.TimeSlotRepository;
import ar.edu.utn.frc.classroom_allocation.schedule.service.TimeSlotService;
import java.time.LocalTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TimeSlotServiceImpl implements TimeSlotService {

    private final TimeSlotRepository timeSlotRepository;

    @Override
    public Optional<TimeSlot> findByDayOfWeekAndStartTimeAndEndTime(
            String dayOfWeek, LocalTime startTime, LocalTime endTime) {
        return timeSlotRepository.findByDayOfWeekAndStartTimeAndEndTime(dayOfWeek, startTime, endTime);
    }

    @Override
    @Transactional
    public TimeSlot save(TimeSlot timeSlot) {
        return timeSlotRepository.save(timeSlot);
    }
}
