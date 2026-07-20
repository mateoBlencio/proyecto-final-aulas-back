package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ClassroomOverlapDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OvercrowdedAllocationDto;
import ar.edu.utn.frc.siga.allocation.mapper.AcademicEventComposer;
import ar.edu.utn.frc.siga.allocation.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.service.AcademicEventService;
import ar.edu.utn.frc.siga.allocation.service.AllocationProblemService;
import ar.edu.utn.frc.siga.common.util.DateRanges;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
 * cross-módulo ni N+1: aulas y eventos se resuelven en un batch cada uno.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AllocationProblemServiceImpl implements AllocationProblemService {

    private final AllocationRepository allocationRepository;
    private final ClassroomService classroomService;
    private final AcademicEventService academicEventService;
    private final AcademicPeriodService academicPeriodService;
    private final AcademicEventComposer academicEventComposer;

    @Override
    public List<AcademicEventResponseDto> findUnassigned(LocalDate from, LocalDate to) {
        Range range = resolveRange(from, to);
        List<AcademicEventResponseDto> unassigned = academicEventService.findUnassignedEvents(range.from(), range.to());
        log.info("Eventos sin aula listados: count={}", unassigned.size());
        return unassigned;
    }

    @Override
    public List<OvercrowdedAllocationDto> findOvercrowded(LocalDate from, LocalDate to) {
        Range range = resolveRange(from, to);
        List<Allocation> occupancy = readOccupancy(range);

        Map<OvercrowdKey, OvercrowdAcc> overcrowdAccs = new LinkedHashMap<>();
        for (Allocation allocation : occupancy) {
            AcademicEvent event = allocation.getOccurrence().getEvent();
            Integer classroomId = allocation.getClassroomId();
            overcrowdAccs.computeIfAbsent(new OvercrowdKey(event.getId(), classroomId),
                            k -> new OvercrowdAcc(event, classroomId))
                    .dates.add(allocation.getOccurrence().getDate());
        }

        Set<AcademicEvent> events = new LinkedHashSet<>();
        Set<Integer> classroomIds = new LinkedHashSet<>();
        for (OvercrowdAcc acc : overcrowdAccs.values()) {
            events.add(acc.event);
            classroomIds.add(acc.classroomId);
        }

        List<OvercrowdedAllocationDto> overcrowded = buildOvercrowded(
                overcrowdAccs, academicEventComposer.composeById(new ArrayList<>(events)), fetchClassroomsById(classroomIds));
        log.info("Aulas con sobrecupo listadas: count={}", overcrowded.size());
        return overcrowded;
    }

    @Override
    public List<ClassroomOverlapDto> findOverlaps(LocalDate from, LocalDate to) {
        Range range = resolveRange(from, to);
        List<Allocation> occupancy = readOccupancy(range);

        Map<GroupKey, List<Allocation>> byClassroomAndDate = new LinkedHashMap<>();
        for (Allocation allocation : occupancy) {
            Occurrence occurrence = allocation.getOccurrence();
            byClassroomAndDate.computeIfAbsent(new GroupKey(allocation.getClassroomId(), occurrence.getDate()),
                    k -> new ArrayList<>()).add(allocation);
        }

        Map<OverlapKey, OverlapAcc> overlapAccs = computeOverlaps(byClassroomAndDate);

        Set<AcademicEvent> events = new LinkedHashSet<>();
        Set<Integer> classroomIds = new LinkedHashSet<>();
        for (OverlapAcc acc : overlapAccs.values()) {
            events.add(acc.eventA);
            events.add(acc.eventB);
            classroomIds.add(acc.classroomId);
        }

        List<ClassroomOverlapDto> overlaps = buildOverlaps(
                overlapAccs, academicEventComposer.composeById(new ArrayList<>(events)), fetchClassroomsById(classroomIds));
        log.info("Superposiciones de horario-aula listadas: count={}", overlaps.size());
        return overlaps;
    }

    private List<Allocation> readOccupancy(Range range) {
        return allocationRepository.findOccupancyBetween(range.from(), range.to(), OccurrenceStatus.ASSIGNED);
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
    private Map<OverlapKey, OverlapAcc> computeOverlaps(Map<GroupKey, List<Allocation>> byClassroomAndDate) {
        Map<OverlapKey, OverlapAcc> overlapAccs = new LinkedHashMap<>();
        for (List<Allocation> group : byClassroomAndDate.values()) {
            if (group.size() < 2) continue;
            group.sort(Comparator.comparing(a -> a.getOccurrence().getEvent().getStartTime()));
            for (int i = 0; i < group.size(); i++) {
                Allocation a = group.get(i);
                AcademicEvent eventA = a.getOccurrence().getEvent();
                for (int j = i + 1; j < group.size(); j++) {
                    Allocation b = group.get(j);
                    AcademicEvent eventB = b.getOccurrence().getEvent();
                    if (!eventB.getStartTime().isBefore(eventA.endTime())) break;
                    if (eventA.getId().equals(eventB.getId())) continue;
                    if (eventA.getStartTime().isBefore(eventB.endTime()) && eventB.getStartTime().isBefore(eventA.endTime())) {
                        AcademicEvent first = eventA.getId() <= eventB.getId() ? eventA : eventB;
                        AcademicEvent second = eventA.getId() <= eventB.getId() ? eventB : eventA;
                        OverlapKey key = new OverlapKey(first.getId(), second.getId(), a.getClassroomId());
                        overlapAccs.computeIfAbsent(key, k -> new OverlapAcc(first, second, a.getClassroomId()))
                                .dates.add(a.getOccurrence().getDate());
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

            Integer enrolled = acc.event.getEnrolled() != null ? acc.event.getEnrolled() : 0;
            Integer capacity = classroom.capacity();
            if (enrolled <= capacity) continue;

            overcrowded.add(new OvercrowdedAllocationDto(
                    eventDtoById.get(acc.event.getId()), classroom, enrolled, capacity, enrolled - capacity,
                    List.copyOf(acc.dates)));
        }
        return overcrowded;
    }

    private List<ClassroomOverlapDto> buildOverlaps(Map<OverlapKey, OverlapAcc> overlapAccs,
            Map<Long, AcademicEventResponseDto> eventDtoById, Map<Integer, ClassroomResponseDto> classroomDtoById) {
        List<ClassroomOverlapDto> overlaps = new ArrayList<>();
        for (OverlapAcc acc : overlapAccs.values()) {
            overlaps.add(new ClassroomOverlapDto(
                    classroomDtoById.get(acc.classroomId),
                    eventDtoById.get(acc.eventA.getId()),
                    eventDtoById.get(acc.eventB.getId()),
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
        final AcademicEvent event;
        final Integer classroomId;
        final Set<LocalDate> dates = new TreeSet<>();

        OvercrowdAcc(AcademicEvent event, Integer classroomId) {
            this.event = event;
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
        final AcademicEvent eventA;
        final AcademicEvent eventB;
        final Integer classroomId;
        final Set<LocalDate> dates = new TreeSet<>();

        OverlapAcc(AcademicEvent eventA, AcademicEvent eventB, Integer classroomId) {
            this.eventA = eventA;
            this.eventB = eventB;
            this.classroomId = classroomId;
        }
    }
}
