package ar.edu.utn.frc.siga.events.service.impl;

import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.UpdateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.SysacadRecurringEventRefDto;
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
import ar.edu.utn.frc.siga.events.service.command.SyncRecurringEventCommand;
import ar.edu.utn.frc.siga.events.service.command.UpsertRecurringEventResult;
import ar.edu.utn.frc.siga.events.validator.EventScheduleValidator;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.Finder;
import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.common.util.RecurringEventKey;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    @Transactional(readOnly = true)
    public List<RecurringEventResponseDto> findRecurringEventsBySubjectAndCommission(Long subjectId, Long commissionId) {
        List<RecurringEvent> events =
                recurringEventRepository.findActiveBySubjectAndCommission(subjectId, commissionId, LocalDate.now());
        return composer.compose(events).stream()
                .map(RecurringEventResponseDto.class::cast)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OccurrenceResponseDto> findClassOccurrences(Long subjectId, Long commissionId, LocalDate from) {
        List<Long> eventIds = recurringEventRepository
                .findActiveBySubjectAndCommission(subjectId, commissionId, LocalDate.now())
                .stream()
                .map(RecurringEvent::getId)
                .toList();
        if (eventIds.isEmpty()) {
            return List.of();
        }
        return occurrenceRepository.findByEvent_IdInAndDateGreaterThanEqual(eventIds, from).stream()
                .map(occurrenceMapper::toDto)
                .sorted(Comparator.comparing(OccurrenceResponseDto::date))
                .toList();
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
    public List<UpsertRecurringEventResult> syncRecurringEvents(List<SyncRecurringEventCommand> commands) {
        Set<Long> subjectIds = commands.stream()
                .map(SyncRecurringEventCommand::subjectId).collect(Collectors.toSet());
        Set<Long> commissionIds = commands.stream()
                .map(SyncRecurringEventCommand::commissionId).collect(Collectors.toSet());
        Map<RecurringEventKey, RecurringEvent> byKey = Maps.byId(
                recurringEventRepository.findBySubjectIdInAndCommissionIdIn(subjectIds, commissionIds),
                AcademicEventServiceImpl::keyOf, (first, ignored) -> first);

        Instant now = Instant.now();
        List<RecurringEvent> resolved = new ArrayList<>(commands.size());
        boolean[] createdFlags = new boolean[commands.size()];
        Set<RecurringEvent> updated = new LinkedHashSet<>();
        List<RecurringEvent> created = new ArrayList<>();

        for (int i = 0; i < commands.size(); i++) {
            SyncRecurringEventCommand cmd = commands.get(i);
            RecurringEventKey key = keyOf(cmd);
            RecurringEvent existing = byKey.get(key);
            if (existing != null) {
                existing.setEnrolled(cmd.enrolled());
                reconcileDuration(existing, cmd.durationMinutes());
                existing.setSyncedAt(now);
                existing.setSysacadEnabled(true);
                if (existing.getId() != null) {
                    updated.add(existing);
                }
                resolved.add(existing);
            } else {
                RecurringEvent event = RecurringEvent.builder()
                        .enrolled(cmd.enrolled())
                        .startTime(cmd.startTime())
                        .duration(Duration.ofMinutes(cmd.durationMinutes()))
                        .dayOfWeek(cmd.dayOfWeek())
                        .startDate(cmd.startDate())
                        .endDate(cmd.endDate())
                        .subjectId(cmd.subjectId())
                        .commissionId(cmd.commissionId())
                        .syncedAt(now)
                        .sysacadHash(Hashes.sha256Hex(cmd.durationMinutes()))
                        .sysacadEnabled(true)
                        .build();
                byKey.put(key, event);
                created.add(event);
                resolved.add(event);
                createdFlags[i] = true;
            }
        }

        recurringEventRepository.saveAll(updated);
        eventRepository.saveAll(created);
        occurrenceRepository.saveAll(created.stream()
                .flatMap(event -> event.toOccurrences().stream())
                .toList());

        List<UpsertRecurringEventResult> results = new ArrayList<>(commands.size());
        for (int i = 0; i < commands.size(); i++) {
            RecurringEvent event = resolved.get(i);
            boolean wasCreated = createdFlags[i];
            results.add(new UpsertRecurringEventResult(event.getId(), wasCreated, !wasCreated));
        }

        log.info("Sync EVENTOS de SysAcad: {} eventos recurrentes creados, {} actualizados",
                created.size(), commands.size() - created.size());
        return results;
    }

    @Override
    @Transactional
    public UpsertRecurringEventResult syncRecurringEvent(SyncRecurringEventCommand cmd) {
        return syncRecurringEvents(List.of(cmd)).getFirst();
    }

    private static RecurringEventKey keyOf(RecurringEvent event) {
        return new RecurringEventKey(event.getSubjectId(), event.getCommissionId(), event.getDayOfWeek(),
                event.getStartTime(), event.getStartDate(), event.getEndDate());
    }

    private static RecurringEventKey keyOf(SyncRecurringEventCommand command) {
        return new RecurringEventKey(command.subjectId(), command.commissionId(), command.dayOfWeek(),
                command.startTime(), command.startDate(), command.endDate());
    }

    /**
     * {@code enrolled} y {@code duration} no se tratan igual: {@code enrolled} siempre se pisa (llamado
     * aparte, sin condición); {@code duration} sólo si nadie la tocó desde la última vez que el sync
     * escribió — mismo mecanismo de hash que Subject/Commission/Classroom, aplicado a un solo campo.
     * {@code sysacadHash} guarda el hash de lo que el sync ESCRIBIÓ la última vez, no el de la fila
     * entrante, así que sirve para detectar drift ("¿lo que hay en la base sigue siendo lo que yo
     * puse?"). Ver .claude/docs/plan-sync-eventos-sysacad.md §4.
     */
    private boolean reconcileDuration(AcademicEvent event, int incomingMinutes) {
        String dbHash = Hashes.sha256Hex(event.getDuration().toMinutes());
        boolean untouchedSinceLastSync = dbHash.equals(event.getSysacadHash());
        String incomingHash = Hashes.sha256Hex(incomingMinutes);

        if (!untouchedSinceLastSync) {
            if (!incomingHash.equals(dbHash)) {
                log.warn("Evento {} tiene duración editada fuera del sync ({} min actual vs {} min entrante); no se pisa",
                        event.getId(), event.getDuration().toMinutes(), incomingMinutes);
            }
            return false;
        }
        if (incomingHash.equals(dbHash)) {
            return false;
        }
        event.setDuration(Duration.ofMinutes(incomingMinutes));
        event.setSysacadHash(incomingHash);
        return true;
    }

    @Override
    @Transactional
    public int markRecurringEventsAbsent(Collection<Long> presentEventIds) {
        Instant now = Instant.now();
        int affected = 0;
        for (RecurringEvent event : recurringEventRepository.findBySysacadHashIsNotNull()) {
            if (presentEventIds.contains(event.getId()) || Boolean.FALSE.equals(event.getSysacadEnabled())) {
                continue;
            }
            event.setSysacadEnabled(false);
            event.setSyncedAt(now);
            recurringEventRepository.save(event);
            affected++;
            log.info("Evento recurrente marcado como no vigente en SysAcad: id={}", event.getId());
        }
        return affected;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findRecurringEventId(Long subjectId, Long commissionId, DayOfWeek dayOfWeek,
            LocalTime startTime, LocalDate startDate, LocalDate endDate) {
        return recurringEventRepository
                .findBySubjectIdAndCommissionIdAndDayOfWeekAndStartTimeAndStartDateAndEndDate(
                        subjectId, commissionId, dayOfWeek, startTime, startDate, endDate)
                .map(RecurringEvent::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SysacadRecurringEventRefDto> findSysacadRecurringEvents() {
        return recurringEventRepository.findBySysacadHashIsNotNull().stream()
                .map(r -> new SysacadRecurringEventRefDto(r.getId(), r.getSubjectId(), r.getCommissionId(),
                        r.getDayOfWeek(), r.getStartTime(), r.getStartDate(), r.getEndDate()))
                .toList();
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
