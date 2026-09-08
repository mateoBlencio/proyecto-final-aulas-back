package ar.edu.utn.frc.siga.events.service.impl;

import ar.edu.utn.frc.siga.events.dto.AcademicEventFilter;
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
import ar.edu.utn.frc.siga.events.model.OccurrenceWindow;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.model.UniqueEvent;
import ar.edu.utn.frc.siga.events.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.repository.RecurringEventRepository;
import ar.edu.utn.frc.siga.events.repository.UniqueEventRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.command.SyncRecurringEventCommand;
import ar.edu.utn.frc.siga.events.service.command.UpsertRecurringEventResult;
import ar.edu.utn.frc.siga.events.specification.AcademicEventSpecification;
import ar.edu.utn.frc.siga.events.validator.EventScheduleValidator;
import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
import java.util.function.BiConsumer;
import java.util.function.Function;
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
    public Page<AcademicEventResponseDto> findAll(AcademicEventFilter filter, Pageable pageable) {
        log.debug("Listando eventos académicos: filter={}, pageable={}", filter, pageable);
        Page<AcademicEvent> page = eventRepository.findAll(
                AcademicEventSpecification.withFilter(filter), pageable);
        return new PageImpl<>(composer.compose(page.getContent()), pageable, page.getTotalElements());
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
        CommissionResponseDto commission = commissionService.findById(dto.commissionId());

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

        eventRepository.save(event);
        List<Occurrence> occurrences = event.toOccurrences(windowFor(commission.academicPeriod()));
        occurrenceRepository.saveAll(occurrences);

        log.info("Evento recurrente creado: id={}, occurrences={}", event.getId(), occurrences.size());
        return composer.compose(event);
    }

    @Override
    @Transactional
    public FindOrCreateResult<Long> findOrCreateRecurringEvent(CreateRecurringEventRequestDto dto) {
        return findOrCreateRecurringEvents(List.of(dto)).getFirst();
    }

    @Override
    @Transactional
    public List<FindOrCreateResult<Long>> findOrCreateRecurringEvents(List<CreateRecurringEventRequestDto> requests) {
        Partition partition = partitionByKey(requests, AcademicEventServiceImpl::keyOf,
                AcademicEventServiceImpl::buildRecurringEvent, null);

        Map<Long, OccurrenceWindow> windowByCommission = requireExistingReferences(partition.created());
        persistCreated(partition.created(), windowByCommission);

        List<FindOrCreateResult<Long>> results = new ArrayList<>(requests.size());
        for (int i = 0; i < requests.size(); i++) {
            results.add(new FindOrCreateResult<>(partition.resolved().get(i).getId(), partition.createdFlags()[i]));
        }
        log.info("Find-or-create bulk de eventos recurrentes: {} creados de {} pedidos",
                partition.created().size(), requests.size());
        return results;
    }

    private static RecurringEvent buildRecurringEvent(CreateRecurringEventRequestDto dto) {
        return RecurringEvent.builder()
                .enrolled(dto.enrolled())
                .startTime(dto.startTime())
                .duration(Duration.ofMinutes(dto.durationMinutes()))
                .dayOfWeek(dto.dayOfWeek())
                .startDate(dto.startDate())
                .endDate(dto.endDate())
                .subjectId(dto.subjectId())
                .commissionId(dto.commissionId())
                .build();
    }

    /**
     * El bulk construye el evento inline (no pasa por createRecurringEvent), así que valida acá el set
     * distinto de referencias que va a crear -- una consulta batch por servicio -- para no insertar
     * eventos con subjectId/commissionId inexistentes. Devuelve la ventana de ocurrencias por comisión
     * derivada del período académico, reutilizando el batch de comisiones que ya trajo.
     */
    private Map<Long, OccurrenceWindow> requireExistingReferences(List<RecurringEvent> toCreate) {
        if (toCreate.isEmpty()) {
            return Map.of();
        }
        Set<Long> subjectIds = toCreate.stream().map(RecurringEvent::getSubjectId).collect(Collectors.toSet());
        Set<Long> commissionIds = toCreate.stream().map(RecurringEvent::getCommissionId).collect(Collectors.toSet());
        Set<Long> knownSubjects = subjectService.findByIds(subjectIds).stream()
                .map(SubjectResponseDto::id).collect(Collectors.toSet());
        subjectIds.stream().filter(id -> !knownSubjects.contains(id)).findFirst()
                .ifPresent(id -> { throw ResourceNotFoundException.of("Subject", id); });
        Map<Long, OccurrenceWindow> windowByCommission = windowsByCommission(commissionIds);
        commissionIds.stream().filter(id -> !windowByCommission.containsKey(id)).findFirst()
                .ifPresent(id -> { throw ResourceNotFoundException.of("Commission", id); });
        return windowByCommission;
    }

    private void persistCreated(List<RecurringEvent> created, Map<Long, OccurrenceWindow> windowByCommission) {
        if (created.isEmpty()) {
            return;
        }
        eventRepository.saveAll(created);
        occurrenceRepository.saveAll(created.stream()
                .flatMap(event -> event.toOccurrences(windowByCommission.getOrDefault(
                        event.getCommissionId(), windowFor(null))).stream())
                .toList());
    }

    @Override
    @Transactional
    public List<UpsertRecurringEventResult> syncRecurringEvents(List<SyncRecurringEventCommand> commands) {
        Instant now = Instant.now();
        Set<RecurringEvent> updated = new LinkedHashSet<>();

        Partition partition = partitionByKey(commands, AcademicEventServiceImpl::keyOf,
                cmd -> buildRecurringEvent(cmd, now),
                (existing, cmd) -> {
                    existing.setEnrolled(cmd.enrolled());
                    reconcileDuration(existing, cmd.durationMinutes());
                    existing.setSyncedAt(now);
                    existing.setSysacadEnabled(true);
                    if (existing.getId() != null) {
                        updated.add(existing);
                    }
                });

        recurringEventRepository.saveAll(updated);
        persistCreated(partition.created(), windowsByCommission(partition.created().stream()
                .map(RecurringEvent::getCommissionId).collect(Collectors.toSet())));

        List<UpsertRecurringEventResult> results = new ArrayList<>(commands.size());
        for (int i = 0; i < commands.size(); i++) {
            boolean wasCreated = partition.createdFlags()[i];
            results.add(new UpsertRecurringEventResult(partition.resolved().get(i).getId(), wasCreated, !wasCreated));
        }

        log.info("Sync EVENTOS de SysAcad: {} eventos recurrentes creados, {} actualizados",
                partition.created().size(), commands.size() - partition.created().size());
        return results;
    }

    private static RecurringEvent buildRecurringEvent(SyncRecurringEventCommand cmd, Instant now) {
        return RecurringEvent.builder()
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
    }

    /**
     * Prefetch por (subjectId, commissionId) del lote, índice por {@link RecurringEventKey} y
     * partición hit/miss preservando el orden de entrada. Lo comparten el find-or-create bulk y el
     * sync: en un miss construye el evento con {@code buildFn}; en un hit, si {@code onExisting} no es
     * null, lo aplica (el sync lo usa para su rama de actualización).
     */
    private <C> Partition partitionByKey(List<C> commands, Function<C, RecurringEventKey> keyFn,
            Function<C, RecurringEvent> buildFn, BiConsumer<RecurringEvent, C> onExisting) {
        List<RecurringEventKey> keys = commands.stream().map(keyFn).toList();
        Set<Long> subjectIds = keys.stream().map(RecurringEventKey::subjectId).collect(Collectors.toSet());
        Set<Long> commissionIds = keys.stream().map(RecurringEventKey::commissionId).collect(Collectors.toSet());
        Map<RecurringEventKey, RecurringEvent> byKey = Maps.byId(
                recurringEventRepository.findBySubjectIdInAndCommissionIdIn(subjectIds, commissionIds),
                AcademicEventServiceImpl::keyOf, (first, ignored) -> first);

        List<RecurringEvent> resolved = new ArrayList<>(commands.size());
        boolean[] createdFlags = new boolean[commands.size()];
        List<RecurringEvent> created = new ArrayList<>();

        for (int i = 0; i < commands.size(); i++) {
            C cmd = commands.get(i);
            RecurringEventKey key = keys.get(i);
            RecurringEvent existing = byKey.get(key);
            if (existing != null) {
                if (onExisting != null) {
                    onExisting.accept(existing, cmd);
                }
                resolved.add(existing);
            } else {
                RecurringEvent event = buildFn.apply(cmd);
                byKey.put(key, event);
                created.add(event);
                resolved.add(event);
                createdFlags[i] = true;
            }
        }
        return new Partition(resolved, createdFlags, created);
    }

    private record Partition(List<RecurringEvent> resolved, boolean[] createdFlags, List<RecurringEvent> created) {
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

    private static RecurringEventKey keyOf(CreateRecurringEventRequestDto dto) {
        return new RecurringEventKey(dto.subjectId(), dto.commissionId(), dto.dayOfWeek(),
                dto.startTime(), dto.startDate(), dto.endDate());
    }

    private Map<Long, OccurrenceWindow> windowsByCommission(Set<Long> commissionIds) {
        if (commissionIds.isEmpty()) {
            return Map.of();
        }
        return commissionService.findByIds(commissionIds).stream()
                .collect(Collectors.toMap(CommissionResponseDto::id,
                        commission -> windowFor(commission.academicPeriod())));
    }

    private static OccurrenceWindow windowFor(AcademicPeriodResponseDto period) {
        if (period == null) {
            return OccurrenceWindow.unbounded(null, null);
        }
        return new OccurrenceWindow(period.startDate(), period.endDate(),
                period.recessStart(), period.recessEnd());
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
