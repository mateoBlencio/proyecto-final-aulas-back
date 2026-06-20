package ar.edu.utn.frc.classroom_allocation.schedule.service.impl;

import ar.edu.utn.frc.classroom_allocation.schedule.model.TimeSlot;
import ar.edu.utn.frc.classroom_allocation.schedule.repository.TimeSlotRepository;
import ar.edu.utn.frc.classroom_allocation.schedule.service.TimeSlotService;
import java.time.LocalTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TimeSlotServiceImpl implements TimeSlotService {

    private final TimeSlotRepository timeSlotRepository;

    @Override
    public Optional<TimeSlot> findByDayOfWeekAndStartTimeAndEndTime(
            String dayOfWeek, LocalTime startTime, LocalTime endTime) {
        log.debug("Finding TimeSlot: day={}, start={}, end={}", dayOfWeek, startTime, endTime);
        return timeSlotRepository.findByDayOfWeekAndStartTimeAndEndTime(dayOfWeek, startTime, endTime);
    }

    @Override
    @Transactional
    public TimeSlot save(TimeSlot timeSlot) {
        log.debug("Saving TimeSlot: day={}, start={}, end={}",
                timeSlot.getDayOfWeek(), timeSlot.getStartTime(), timeSlot.getEndTime());
        TimeSlot saved = timeSlotRepository.save(timeSlot);
        log.info("TimeSlot saved: id={}", saved.getId());
        return saved;
    }
}
