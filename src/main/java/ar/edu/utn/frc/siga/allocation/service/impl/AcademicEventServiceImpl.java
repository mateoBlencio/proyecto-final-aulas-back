package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.common.util.DateRanges;
import ar.edu.utn.frc.siga.allocation.mapper.AcademicEventComposer;
import ar.edu.utn.frc.siga.allocation.mapper.OccurrenceMapper;
import ar.edu.utn.frc.siga.allocation.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.model.UniqueEvent;
import ar.edu.utn.frc.siga.allocation.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.allocation.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.allocation.repository.RecurringEventRepository;
import ar.edu.utn.frc.siga.allocation.service.AcademicEventService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.Finder;
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

/**
 * Implementación de alta y consulta de eventos académicos (recurrentes/únicos): valida
 * la existencia de materia/comisión ajenas vía sus fachadas, genera las occurrences del
 * evento al crearlo, y resuelve el listado de eventos pendientes de asignación de aula.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcademicEventServiceImpl implements AcademicEventService {

    private final AcademicEventRepository eventRepository;
    private final RecurringEventRepository recurringEventRepository;
    private final OccurrenceRepository occurrenceRepository;
    private final AcademicEventComposer composer;
    private final OccurrenceMapper occurrenceMapper;
    private final SubjectService subjectService;
    private final CommissionService commissionService;

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
     * Busca un evento recurrente idéntico (misma materia/comisión/día/horario/ventana de
     * fechas): catálogo cargado por fuera de esta app, falla si no existe.
     */
    @Override
    @Transactional(readOnly = true)
    public Long findRecurringEvent(CreateRecurringEventRequestDto dto) {
        return recurringEventRepository
                .findBySubjectIdAndCommissionIdAndDayOfWeekAndStartTimeAndStartDateAndEndDate(
                        dto.subjectId(), dto.commissionId(), dto.dayOfWeek(), dto.startTime(),
                        dto.startDate(), dto.endDate())
                .map(RecurringEvent::getId)
                .orElseThrow(() -> ResourceNotFoundException.of("RecurringEvent",
                        dto.subjectId() + "-" + dto.commissionId() + "-" + dto.dayOfWeek() + "-" + dto.startTime()));
    }

    /** Crea un evento único y genera su única occurrence, sin aula (SCHEDULED). */
    @Override
    @Transactional
    public AcademicEventResponseDto createUniqueEvent(CreateUniqueEventRequestDto dto) {
        log.debug("Creando evento único: date={}", dto.date());

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

        log.info("Evento único creado: id={}", saved.getId());
        return composer.compose(saved);
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

        return composer.compose(eventById.values());
    }
}