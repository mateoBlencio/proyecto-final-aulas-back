package ar.edu.utn.frc.classroom_allocation.allocation.service.impl;

import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.classroom_allocation.allocation.exception.AcademicEventNotFoundException;
import ar.edu.utn.frc.classroom_allocation.allocation.mapper.AcademicEventMapper;
import ar.edu.utn.frc.classroom_allocation.allocation.model.AcademicEvent;
import ar.edu.utn.frc.classroom_allocation.allocation.model.Occurrence;
import ar.edu.utn.frc.classroom_allocation.allocation.model.RecurringEvent;
import ar.edu.utn.frc.classroom_allocation.allocation.model.UniqueEvent;
import ar.edu.utn.frc.classroom_allocation.allocation.repository.AcademicEventRepository;
import ar.edu.utn.frc.classroom_allocation.allocation.repository.OccurrenceRepository;
import ar.edu.utn.frc.classroom_allocation.allocation.service.AcademicEventService;
import ar.edu.utn.frc.classroom_allocation.career.model.Subject;
import ar.edu.utn.frc.classroom_allocation.career.repository.SubjectRepository;
import ar.edu.utn.frc.classroom_allocation.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.classroom_allocation.course.model.Commission;
import ar.edu.utn.frc.classroom_allocation.course.repository.CommissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AcademicEventServiceImpl implements AcademicEventService {

    private final AcademicEventRepository eventRepository;
    private final OccurrenceRepository occurrenceRepository;
    private final AcademicEventMapper mapper;
    private final SubjectRepository subjectRepository;
    private final CommissionRepository commissionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AcademicEventResponseDto> findAll() {
        log.debug("Listing all academic events");
        return eventRepository.findAll().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicEventResponseDto findById(Long eventId) {
        return mapper.toDto(eventRepository.findById(eventId)
                .orElseThrow(() -> new AcademicEventNotFoundException(eventId)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OccurrenceResponseDto> findOccurrencesByEventId(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new AcademicEventNotFoundException(eventId);
        }
        return occurrenceRepository.findByEvent_Id(eventId).stream()
                .map(o -> OccurrenceResponseDto.builder()
                        .id(o.getId())
                        .eventId(eventId)
                        .date(o.getDate())
                        .status(o.getStatus())
                        .startTime(o.startTime())
                        .endTime(o.endTime())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AcademicEventResponseDto createRecurringEvent(CreateRecurringEventRequestDto dto) {
        log.debug("Creating recurring event: subjectId={}, commissionId={}, dayOfWeek={}, startDate={}",
                dto.subjectId(), dto.commissionId(), dto.dayOfWeek(), dto.startDate());

        Subject subject = subjectRepository.findById(dto.subjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found: " + dto.subjectId()));
        Commission commission = commissionRepository.findById(dto.commissionId())
                .orElseThrow(() -> new ResourceNotFoundException("Commission not found: " + dto.commissionId()));

        RecurringEvent event = RecurringEvent.builder()
                .enrolled(dto.enrolled())
                .startTime(dto.startTime())
                .duration(Duration.ofMinutes(dto.durationMinutes()))
                .dayOfWeek(dto.dayOfWeek())
                .startDate(dto.startDate())
                .endDate(dto.endDate())
                .subject(subject)
                .commission(commission)
                .build();

        AcademicEvent saved = eventRepository.save(event);
        List<Occurrence> occurrences = saved.toOccurrences();
        occurrenceRepository.saveAll(occurrences);

        log.info("Recurring event created: id={}, occurrences={}", saved.getId(), occurrences.size());
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public AcademicEventResponseDto createUniqueEvent(CreateUniqueEventRequestDto dto) {
        log.debug("Creating unique event: date={}", dto.date());

        UniqueEvent event = UniqueEvent.builder()
                .enrolled(dto.enrolled())
                .startTime(dto.startTime())
                .duration(Duration.ofMinutes(dto.durationMinutes()))
                .date(dto.date())
                .description(dto.description())
                .build();

        AcademicEvent saved = eventRepository.save(event);
        List<Occurrence> occurrences = saved.toOccurrences();
        occurrenceRepository.saveAll(occurrences);

        log.info("Unique event created: id={}", saved.getId());
        return mapper.toDto(saved);
    }
}
