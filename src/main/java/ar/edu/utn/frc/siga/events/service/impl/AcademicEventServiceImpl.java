package ar.edu.utn.frc.siga.events.service.impl;

import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.UpdateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.common.util.DateRanges;
import ar.edu.utn.frc.siga.events.mapper.AcademicEventComposer;
import ar.edu.utn.frc.siga.events.mapper.OccurrenceMapper;
import ar.edu.utn.frc.siga.events.model.AcademicEvent;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
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
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementación de alta y consulta de eventos académicos (recurrentes/únicos): valida
 * la existencia de materia/comisión ajenas vía sus fachadas, genera las occurrences del
 * evento al crearlo, y resuelve el listado de eventos pendientes de asignación de aula.
 * No conoce aulas ni asignaciones: {@code createUniqueEvent}/{@code updateUniqueEvent} crean
 * o modifican el evento y su occurrence, nada más — la asignación atómica de aula la
 * orquesta {@code allocation} (ver {@code UniqueEventAllocationService}).
 */
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

    /**
     * Crea un evento recurrente (validando primero que materia y comisión existan vía sus
     * fachadas) y genera de una vez todas sus occurrences semanales, sin aula (SCHEDULED).
     */
    @Override
    @Transactional
    public AcademicEventResponseDto createRecurringEvent(CreateRecurringEventRequestDto dto) {
        log.debug("Creando evento recurrente: subjectId={}, commissionId={}, dayOfWeek={}, startDate={}",
                dto.subjectId(), dto.commissionId(), dto.dayOfWeek(), dto.startDate());

        // Solo se valida existencia vía la fachada (404 si no existe); no se necesita el DTO completo.
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

    /**
     * Reutiliza un evento recurrente idéntico (misma materia/comisión/día/horario/ventana
     * de fechas) si ya existe; si no, lo crea. Pensado para importaciones donde varias
     * filas de la planilla describen el mismo evento.
     */
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

    /** Crea un evento único y genera su única occurrence (SCHEDULED, sin aula). */
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

    /** Lista todos los eventos únicos (parciales, TPs, mesas especiales, etc.). */
    @Override
    @Transactional(readOnly = true)
    public List<AcademicEventResponseDto> findUniqueEvents() {
        log.debug("Listando eventos únicos");
        return composer.compose(uniqueEventRepository.findAll());
    }

    /**
     * Modifica un evento único existente y la fecha de su occurrence, revalidando ventana
     * horaria y referencia académica (mismo camino que el alta). {@code id} que no corresponde
     * a un evento único (inexistente o recurrente) → 404, ya que {@link UniqueEventRepository}
     * solo resuelve filas de {@code evento_unico_academico}. Rechaza si la occurrence ya ocurrió.
     */
    @Override
    @Transactional
    public AcademicEventResponseDto updateUniqueEvent(Long id, UpdateUniqueEventRequestDto dto) {
        log.debug("Actualizando evento único: id={}", id);

        UniqueEvent event = Finder.orThrow(uniqueEventRepository::findById, id, "UniqueEvent");
        Occurrence occurrence = occurrenceRepository.findByEvent_Id(id).getFirst();

        // Se valida el estado ANTES de mutar la occurrence: isPast() debe evaluarse contra
        // la fecha/hora vigente, no la nueva que se está por escribir.
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

    /**
     * Baja lógica: cancela la única occurrence del evento (sin borrado físico). Una vez
     * CANCELLED, deja de contar como ocupación y libera el aula para nuevas asignaciones
     * sin tocar el registro histórico.
     */
    @Override
    @Transactional
    public void cancelUniqueEvent(Long id) {
        log.debug("Cancelando evento único: id={}", id);

        Finder.orThrow(uniqueEventRepository::findById, id, "UniqueEvent");
        Occurrence occurrence = occurrenceRepository.findByEvent_Id(id).getFirst();
        eventScheduleValidator.validateNotPast(occurrence);

        occurrence.setStatus(OccurrenceStatus.CANCELLED);

        log.info("Evento único cancelado: id={}", id);
    }

    /**
     * Agrupa por evento las occurrences en SCHEDULED entre {@code from} (default hoy) y
     * {@code to} (sin límite superior si es null); excluye ASSIGNED/CANCELLED/SUSPENDED y
     * las ya pasadas (fecha+hora de inicio, ver {@link Occurrence#isPast()}).
     * Rechaza el rango si {@code to} es anterior a {@code from}.
     */
    @Override
    @Transactional(readOnly = true)
    public List<AcademicEventResponseDto> findUnassignedEvents(LocalDate from, LocalDate to, boolean includePast) {
        return composer.compose(groupUnassignedEvents(from, to, includePast).values());
    }

    /**
     * Igual criterio, pero devuelve solo los IDs sin componer el DTO completo (subject,
     * comisión): pensado para resolver selecciones masivas.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Long> findUnassignedEventIds(LocalDate from, LocalDate to, boolean includePast) {
        return List.copyOf(groupUnassignedEvents(from, to, includePast).keySet());
    }

    private Map<Long, AcademicEvent> groupUnassignedEvents(LocalDate from, LocalDate to, boolean includePast) {
        LocalDate effectiveFrom = DateRanges.defaultFrom(from);
        DateRanges.requireNotBefore(to, effectiveFrom);

        List<Occurrence> occurrences = to != null
                ? occurrenceRepository.findByStatusAndDateBetweenOrderByEvent_IdAscDateAsc(
                        OccurrenceStatus.SCHEDULED, effectiveFrom, to)
                : occurrenceRepository.findByStatusAndDateGreaterThanEqualOrderByEvent_IdAscDateAsc(
                        OccurrenceStatus.SCHEDULED, effectiveFrom);

        Map<Long, AcademicEvent> eventById = new LinkedHashMap<>();
        for (Occurrence occurrence : occurrences) {
            if (!includePast && occurrence.isPast()) continue;
            AcademicEvent event = occurrence.getEvent();
            eventById.putIfAbsent(event.getId(), event);
        }
        return eventById;
    }
}
