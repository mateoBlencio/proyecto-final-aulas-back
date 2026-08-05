package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ClassroomOverlapDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OvercrowdedAllocationDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.service.AllocationProblemService;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator.OccupiedSlot;
import ar.edu.utn.frc.siga.common.util.DateRanges;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.common.util.Paging;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Cada tipo de problema se expone por separado. Sobrecupo y superposición se calculan
 * en memoria (O(n log n)) sobre una única lectura de la ocupación asignada del rango;
 * el listado de eventos sin aula delega en {@code AcademicEventService}. Sin joins
 * cross-módulo ni N+1: eventos, aulas y ocupación se resuelven en un batch cada uno.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AllocationProblemServiceImpl implements AllocationProblemService {

    private final AllocationRepository allocationRepository;
    private final OccurrenceService occurrenceService;
    private final ClassroomService classroomService;
    private final AcademicEventService academicEventService;
    private final AcademicPeriodService academicPeriodService;

    @Override
    public Page<AcademicEventResponseDto> findUnassigned(LocalDate from, LocalDate to, boolean includePast, Pageable pageable) {
        Range range = resolveRange(from, to);
        List<AcademicEventResponseDto> unassigned =
                academicEventService.findUnassignedEvents(range.from(), range.to(), includePast);
        log.info("Eventos sin aula listados: count={}", unassigned.size());
        return Paging.of(unassigned, pageable);
    }

    @Override
    public Page<OvercrowdedAllocationDto> findOvercrowded(LocalDate from, LocalDate to, boolean includePast, Pageable pageable) {
        Range range = resolveRange(from, to);
        List<OccupiedSlot> occupancy = readOccupancy(range, includePast);

        Map<OvercrowdKey, OvercrowdAcc> overcrowdAccs = new LinkedHashMap<>();
        for (OccupiedSlot slot : occupancy) {
            overcrowdAccs.computeIfAbsent(new OvercrowdKey(slot.eventId(), slot.classroomId()),
                            k -> new OvercrowdAcc(slot.eventId(), slot.classroomId()))
                    .dates.add(slot.date());
        }

        Set<Long> eventIds = new LinkedHashSet<>();
        Set<Integer> classroomIds = new LinkedHashSet<>();
        for (OvercrowdAcc acc : overcrowdAccs.values()) {
            eventIds.add(acc.eventId);
            classroomIds.add(acc.classroomId);
        }

        List<OvercrowdedAllocationDto> overcrowded = buildOvercrowded(overcrowdAccs,
                fetchEventsById(eventIds), fetchClassroomsById(classroomIds));
        log.info("Aulas con sobrecupo listadas: count={}", overcrowded.size());
        return Paging.of(overcrowded, pageable);
    }

    @Override
    public Page<ClassroomOverlapDto> findOverlaps(LocalDate from, LocalDate to, boolean includePast, Pageable pageable) {
        Range range = resolveRange(from, to);
        List<OccupiedSlot> occupancy = readOccupancy(range, includePast);

        Map<GroupKey, List<OccupiedSlot>> byClassroomAndDate = new LinkedHashMap<>();
        for (OccupiedSlot slot : occupancy) {
            byClassroomAndDate.computeIfAbsent(new GroupKey(slot.classroomId(), slot.date()),
                    k -> new ArrayList<>()).add(slot);
        }

        Map<OverlapKey, OverlapAcc> overlapAccs = computeOverlaps(byClassroomAndDate);

        Set<Long> eventIds = new LinkedHashSet<>();
        Set<Integer> classroomIds = new LinkedHashSet<>();
        for (OverlapAcc acc : overlapAccs.values()) {
            eventIds.add(acc.eventIdA);
            eventIds.add(acc.eventIdB);
            classroomIds.add(acc.classroomId);
        }

        List<ClassroomOverlapDto> overlaps = buildOverlaps(overlapAccs,
                fetchEventsById(eventIds), fetchClassroomsById(classroomIds));
        log.info("Superposiciones de horario-aula listadas: count={}", overlaps.size());
        return Paging.of(overlaps, pageable);
    }

    @Override
    public List<Long> resolveAllUnassignedEventIds() {
        Range range = resolveRange(null, null);
        List<Long> ids = academicEventService.findUnassignedEventIds(range.from(), range.to(), false);
        log.info("Resolución de selección masiva: eventos sin aula count={}", ids.size());
        return ids;
    }

    private List<OccupiedSlot> readOccupancy(Range range, boolean includePast) {
        Map<Long, OccurrenceSlotDto> slotByOccurrenceId = Maps.byId(
                occurrenceService.findSlotsByStatusBetween(OccurrenceStatus.ASSIGNED, range.from(), range.to()),
                OccurrenceSlotDto::occurrenceId);
        return allocationRepository.findByOccurrenceIdIn(slotByOccurrenceId.keySet()).stream()
                .map(a -> OccupiedSlot.from(a, slotByOccurrenceId.get(a.getOccurrenceId())))
                .filter(slot -> includePast || !isPast(slot))
                .toList();
    }

    private boolean isPast(OccupiedSlot slot) {
        return LocalDateTime.now().isAfter(slot.date().atTime(slot.startTime()));
    }

    private Map<Long, AcademicEventResponseDto> fetchEventsById(Set<Long> eventIds) {
        return Maps.byId(academicEventService.findByIds(eventIds), AcademicEventResponseDto::id);
    }

    private Map<Integer, ClassroomResponseDto> fetchClassroomsById(Set<Integer> ids) {
        return Maps.byId(classroomService.findByIds(ids), ClassroomResponseDto::id);
    }

    /** Resuelve el rango efectivo compartido por los tres listados y valida {@code to >= from}. */
    private Range resolveRange(LocalDate from, LocalDate to) {
        LocalDate effectiveFrom = DateRanges.defaultFrom(from);
        LocalDate effectiveTo = to != null ? to : resolveDefaultTo(effectiveFrom);
        DateRanges.requireNotBefore(effectiveTo, effectiveFrom);
        log.debug("Rango de problemas de asignación: from={}, to={}", effectiveFrom, effectiveTo);
        return new Range(effectiveFrom, effectiveTo);
    }

    /**
     * Máximo {@code endDate} de los períodos académicos activos; si no hay período
     * activo o ninguno tiene {@code endDate}, {@code from + 6 meses}.
     */
    private LocalDate resolveDefaultTo(LocalDate from) {
        return academicPeriodService.findActive().stream()
                .map(AcademicPeriodResponseDto::endDate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElseGet(() -> from.plusMonths(6));
    }

    /**
     * Superposiciones: dentro de cada grupo (aula, fecha) ordena por hora de inicio y
     * barre con corte temprano, evitando el producto cartesiano. Los pares se agregan
     * por (eventoA, eventoB, aula) acumulando todas las fechas en que chocan.
     */
    private Map<OverlapKey, OverlapAcc> computeOverlaps(Map<GroupKey, List<OccupiedSlot>> byClassroomAndDate) {
        Map<OverlapKey, OverlapAcc> overlapAccs = new LinkedHashMap<>();
        for (List<OccupiedSlot> group : byClassroomAndDate.values()) {
            if (group.size() < 2) continue;
            group.sort(Comparator.comparing(OccupiedSlot::startTime));
            for (int i = 0; i < group.size(); i++) {
                OccupiedSlot a = group.get(i);
                for (int j = i + 1; j < group.size(); j++) {
                    OccupiedSlot b = group.get(j);
                    if (!b.startTime().isBefore(a.endTime())) break;
                    if (a.eventId().equals(b.eventId())) continue;
                    if (a.startTime().isBefore(b.endTime()) && b.startTime().isBefore(a.endTime())) {
                        Long firstId = a.eventId() <= b.eventId() ? a.eventId() : b.eventId();
                        Long secondId = a.eventId() <= b.eventId() ? b.eventId() : a.eventId();
                        OverlapKey key = new OverlapKey(firstId, secondId, a.classroomId());
                        overlapAccs.computeIfAbsent(key, k -> new OverlapAcc(firstId, secondId, a.classroomId()))
                                .dates.add(a.date());
                    }
                }
            }
        }
        return overlapAccs;
    }

    private List<OvercrowdedAllocationDto> buildOvercrowded(Map<OvercrowdKey, OvercrowdAcc> overcrowdAccs,
            Map<Long, AcademicEventResponseDto> eventDtoById, Map<Integer, ClassroomResponseDto> classroomDtoById) {
        List<OvercrowdedAllocationDto> overcrowded = new ArrayList<>();
        for (OvercrowdAcc acc : overcrowdAccs.values()) {
            ClassroomResponseDto classroom = classroomDtoById.get(acc.classroomId);
            if (classroom == null || classroom.capacity() == null) continue;

            AcademicEventResponseDto event = eventDtoById.get(acc.eventId);
            Integer enrolled = event != null && event.enrolled() != null ? event.enrolled() : 0;
            Integer capacity = classroom.capacity();
            if (enrolled <= capacity) continue;

            overcrowded.add(new OvercrowdedAllocationDto(
                    event, classroom, enrolled, capacity, enrolled - capacity, List.copyOf(acc.dates)));
        }
        return overcrowded;
    }

    private List<ClassroomOverlapDto> buildOverlaps(Map<OverlapKey, OverlapAcc> overlapAccs,
            Map<Long, AcademicEventResponseDto> eventDtoById, Map<Integer, ClassroomResponseDto> classroomDtoById) {
        List<ClassroomOverlapDto> overlaps = new ArrayList<>();
        for (OverlapAcc acc : overlapAccs.values()) {
            overlaps.add(new ClassroomOverlapDto(
                    classroomDtoById.get(acc.classroomId),
                    eventDtoById.get(acc.eventIdA),
                    eventDtoById.get(acc.eventIdB),
                    List.copyOf(acc.dates)));
        }
        return overlaps;
    }

    /** Rango efectivo resuelto (ambos extremos no nulos, {@code to >= from}). */
    private record Range(LocalDate from, LocalDate to) {
    }

    /** Clave de agrupación para sobrecupo: un evento puede tener sobrecupo en más de un aula a lo largo del rango. */
    private record OvercrowdKey(Long eventId, Integer classroomId) {
    }

    private static final class OvercrowdAcc {
        final Long eventId;
        final Integer classroomId;
        final Set<LocalDate> dates = new TreeSet<>();

        OvercrowdAcc(Long eventId, Integer classroomId) {
            this.eventId = eventId;
            this.classroomId = classroomId;
        }
    }

    /** Clave de agrupación para el barrido de superposiciones: mismo aula, misma fecha. */
    private record GroupKey(Integer classroomId, LocalDate date) {
    }

    /** Clave de agregación de un par en conflicto: normalizada por (min id, max id, aula). */
    private record OverlapKey(Long eventIdA, Long eventIdB, Integer classroomId) {
    }

    private static final class OverlapAcc {
        final Long eventIdA;
        final Long eventIdB;
        final Integer classroomId;
        final Set<LocalDate> dates = new TreeSet<>();

        OverlapAcc(Long eventIdA, Long eventIdB, Integer classroomId) {
            this.eventIdA = eventIdA;
            this.eventIdB = eventIdB;
            this.classroomId = classroomId;
        }
    }
}
