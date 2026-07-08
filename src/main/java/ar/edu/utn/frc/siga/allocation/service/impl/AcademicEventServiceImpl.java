package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.common.exception.InvalidDateRangeException;
import ar.edu.utn.frc.siga.allocation.mapper.AcademicEventComposer;
import ar.edu.utn.frc.siga.allocation.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.model.UniqueEvent;
import ar.edu.utn.frc.siga.allocation.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.allocation.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.allocation.repository.RecurringEventRepository;
import ar.edu.utn.frc.siga.allocation.service.AcademicEventService;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AcademicEventServiceImpl implements AcademicEventService {

    private final AcademicEventRepository eventRepository;
    private final RecurringEventRepository recurringEventRepository;
    private final OccurrenceRepository occurrenceRepository;
    private final AcademicEventComposer composer;
    private final SubjectService subjectService;
    private final CommissionService commissionService;

    @Override
    @Transactional(readOnly = true)
    public List<AcademicEventResponseDto> findAll() {
        log.debug("Listing all academic events");
        return composer.compose(eventRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicEventResponseDto findById(Long eventId) {
        return composer.compose(eventRepository.findById(eventId)
                .orElseThrow(() -> ResourceNotFoundException.of("AcademicEvent", eventId)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OccurrenceResponseDto> findOccurrencesByEventId(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw ResourceNotFoundException.of("AcademicEvent", eventId);
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

        Subject subject = subjectService.findById(dto.subjectId())
                .orElseThrow(() -> ResourceNotFoundException.of("Subject", dto.subjectId()));
        Commission commission = commissionService.findById(dto.commissionId())
                .orElseThrow(() -> ResourceNotFoundException.of("Commission", dto.commissionId()));

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
        return composer.compose(saved);
    }

    @Override
    @Transactional
    public FindOrCreateResult<AcademicEventResponseDto> findOrCreateRecurringEvent(CreateRecurringEventRequestDto dto) {
        return recurringEventRepository
                .findBySubject_IdAndCommission_IdAndDayOfWeekAndStartTimeAndStartDateAndEndDate(
                        dto.subjectId(), dto.commissionId(), dto.dayOfWeek(), dto.startTime(),
                        dto.startDate(), dto.endDate())
                .map(existing -> {
                    log.debug("Reusing existing recurring event: id={}", existing.getId());
                    return new FindOrCreateResult<>(composer.compose(existing), false);
                })
                .orElseGet(() -> new FindOrCreateResult<>(createRecurringEvent(dto), true));
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
        return composer.compose(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AcademicEventResponseDto> findUnassignedEvents(LocalDate from, LocalDate to) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now();
        if (to != null && to.isBefore(effectiveFrom)) {
            throw new InvalidDateRangeException(
                    "'to' (" + to + ") no puede ser anterior a 'from' (" + effectiveFrom + ")");
        }

        List<Occurrence> occurrences = to != null
                ? occurrenceRepository.findByStatusAndDateBetweenOrderByEvent_IdAscDateAsc(
                        OccurrenceStatus.SCHEDULED, effectiveFrom, to)
                : occurrenceRepository.findByStatusAndDateGreaterThanEqualOrderByEvent_IdAscDateAsc(
                        OccurrenceStatus.SCHEDULED, effectiveFrom);

        Map<Long, AcademicEvent> eventById = new LinkedHashMap<>();
        for (Occurrence occurrence : occurrences) {
            AcademicEvent event = occurrence.getEvent();
            eventById.putIfAbsent(event.getId(), event);
        }

        return composer.compose(eventById.values());
    }
}