package ar.edu.utn.frc.siga.events.service.impl;

import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.UpdateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.events.mapper.AcademicEventComposer;
import ar.edu.utn.frc.siga.events.mapper.OccurrenceMapper;
import ar.edu.utn.frc.siga.events.model.AcademicEvent;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.model.UniqueEvent;
import ar.edu.utn.frc.siga.events.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.repository.RecurringEventRepository;
import ar.edu.utn.frc.siga.events.repository.UniqueEventRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.validator.EventScheduleValidator;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.Finder;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AcademicEventServiceImpl implements AcademicEventService {

    private final AcademicEventRepository eventRepository;
    private final RecurringEventRepository recurringEventRepository;
    private final UniqueEventRepository uniqueEventRepository;
    private final OccurrenceRepository occurrenceRepository;
    private final AcademicEventComposer composer;
    private final OccurrenceMapper occurrenceMapper;
    private final SubjectService subjectService;
    private final CommissionService commissionService;
    private final EventScheduleValidator eventScheduleValidator;

    @Override
    @Transactional(readOnly = true)
    public List<AcademicEventResponseDto> findAll() {
        log.debug("Listando todos los eventos académicos");
        return composer.compose(eventRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicEventResponseDto findById(Long eventId) {
        return composer.compose(Finder.orThrow(eventRepository::findById, eventId, "AcademicEvent"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AcademicEventResponseDto> findByIds(Collection<Long> eventIds) {
        return composer.compose(eventRepository.findAllById(eventIds));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OccurrenceResponseDto> findOccurrencesByEventId(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw ResourceNotFoundException.of("AcademicEvent", eventId);
        }
        return occurrenceRepository.findByEvent_Id(eventId).stream()
                .map(occurrenceMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AcademicEventResponseDto createRecurringEvent(CreateRecurringEventRequestDto dto) {
        log.debug("Creando evento recurrente: subjectId={}, commissionId={}, dayOfWeek={}, startDate={}",
                dto.subjectId(), dto.commissionId(), dto.dayOfWeek(), dto.startDate());

        subjectService.findById(dto.subjectId());
        commissionService.findById(dto.commissionId());

        RecurringEvent event = RecurringEvent.builder()
                .enrolled(dto.enrolled())
                .startTime(dto.startTime())
                .duration(Duration.ofMinutes(dto.durationMinutes()))
                .dayOfWeek(dto.dayOfWeek())
                .startDate(dto.startDate())
                .endDate(dto.endDate())
                .subjectId(dto.subjectId())
                .commissionId(dto.commissionId())
                .build();

        AcademicEvent saved = eventRepository.save(event);
        List<Occurrence> occurrences = saved.toOccurrences();
        occurrenceRepository.saveAll(occurrences);

        log.info("Evento recurrente creado: id={}, occurrences={}", saved.getId(), occurrences.size());
        return composer.compose(saved);
    }

    @Override
    @Transactional
    public FindOrCreateResult<Long> findOrCreateRecurringEvent(CreateRecurringEventRequestDto dto) {
        return recurringEventRepository
                .findBySubjectIdAndCommissionIdAndDayOfWeekAndStartTimeAndStartDateAndEndDate(
                        dto.subjectId(), dto.commissionId(), dto.dayOfWeek(), dto.startTime(),
                        dto.startDate(), dto.endDate())
                .map(existing -> {
                    log.debug("Reutilizando evento recurrente existente: id={}", existing.getId());
                    return new FindOrCreateResult<>(existing.getId(), false);
                })
                .orElseGet(() -> new FindOrCreateResult<>(createRecurringEvent(dto).id(), true));
    }

    @Override
    @Transactional
    public AcademicEventResponseDto createUniqueEvent(CreateUniqueEventRequestDto dto) {
        log.debug("Creando evento único: eventType={}, subjectId={}, commissionId={}, date={}",
                dto.eventType(), dto.subjectId(), dto.commissionId(), dto.date());

        Duration duration = Duration.ofMinutes(dto.durationMinutes());
        eventScheduleValidator.validateBusinessHours(dto.startTime(), dto.startTime().plus(duration));
        eventScheduleValidator.validateAcademicReference(dto.eventType(), dto.subjectId(), dto.commissionId());
        if (dto.subjectId() != null) {
            subjectService.findById(dto.subjectId());
        }
        if (dto.commissionId() != null) {
            commissionService.findById(dto.commissionId());
        }
        eventScheduleValidator.validateCommissionBelongsToSubject(dto.subjectId(), dto.commissionId());

        UniqueEvent event = UniqueEvent.builder()
                .enrolled(dto.enrolled())
                .startTime(dto.startTime())
                .duration(duration)
                .date(dto.date())
                .description(dto.description())
                .kind(dto.eventType())
                .subjectId(dto.subjectId())
                .commissionId(dto.commissionId())
                .build();

        AcademicEvent saved = eventRepository.save(event);
        List<Occurrence> occurrences = saved.toOccurrences();
        occurrenceRepository.saveAll(occurrences);

        log.info("Evento único creado: id={}", saved.getId());
        return composer.compose(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AcademicEventResponseDto> findUniqueEvents() {
        log.debug("Listando eventos únicos");
        return composer.compose(uniqueEventRepository.findAll());
    }

    @Override
    @Transactional
    public AcademicEventResponseDto updateUniqueEvent(Long id, UpdateUniqueEventRequestDto dto) {
        log.debug("Actualizando evento único: id={}", id);

        UniqueEvent event = Finder.orThrow(uniqueEventRepository::findById, id, "UniqueEvent");
        Occurrence occurrence = occurrenceRepository.findByEvent_Id(id).getFirst();

        eventScheduleValidator.validateNotPast(occurrence);

        Duration duration = Duration.ofMinutes(dto.durationMinutes());
        eventScheduleValidator.validateBusinessHours(dto.startTime(), dto.startTime().plus(duration));
        eventScheduleValidator.validateAcademicReference(dto.eventType(), dto.subjectId(), dto.commissionId());
        if (dto.subjectId() != null) {
            subjectService.findById(dto.subjectId());
        }
        if (dto.commissionId() != null) {
            commissionService.findById(dto.commissionId());
        }
        eventScheduleValidator.validateCommissionBelongsToSubject(dto.subjectId(), dto.commissionId());

        event.setEnrolled(dto.enrolled());
        event.setStartTime(dto.startTime());
        event.setDuration(duration);
        event.setDate(dto.date());
        event.setDescription(dto.description());
        event.setKind(dto.eventType());
        event.setSubjectId(dto.subjectId());
        event.setCommissionId(dto.commissionId());
        occurrence.setDate(dto.date());

        log.info("Evento único actualizado: id={}", id);
        return composer.compose(event);
    }

}
