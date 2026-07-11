package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationProblemsResponseDto;
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
import ar.edu.utn.frc.siga.common.exception.InvalidDateRangeException;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Detecta sobrecupo y superposiciones a partir de una única lectura de la ocupación
 * asignada en el rango ({@link AllocationRepository#findOccupancyBetween}); el listado
 * de eventos sin aula delega en {@link AcademicEventService#findUnassignedEvents}.
 * Todo el cómputo de agrupación es en memoria (O(n log n)) sobre ese único result set,
 * sin joins cross-módulo ni N+1: aulas y eventos se resuelven en un batch cada uno.
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
    public AllocationProblemsResponseDto findProblems(LocalDate from, LocalDate to) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now();
        LocalDate effectiveTo = to != null ? to : resolveDefaultTo(effectiveFrom);
        if (effectiveTo.isBefore(effectiveFrom)) {
            throw new InvalidDateRangeException(
                    "'to' (" + effectiveTo + ") no puede ser anterior a 'from' (" + effectiveFrom + ")");
        }

        log.debug("Buscando problemas de asignación: from={}, to={}", effectiveFrom, effectiveTo);

        List<AcademicEventResponseDto> unassigned = academicEventService.findUnassignedEvents(effectiveFrom, effectiveTo);

        List<Allocation> occupancy = allocationRepository.findOccupancyBetween(
                effectiveFrom, effectiveTo, OccurrenceStatus.ASSIGNED);

        Map<OvercrowdKey, OvercrowdAcc> overcrowdAccs = new LinkedHashMap<>();
        Map<GroupKey, List<Allocation>> byClassroomAndDate = new LinkedHashMap<>();

        for (Allocation allocation : occupancy) {
            Occurrence occurrence = allocation.getOccurrence();
            AcademicEvent event = occurrence.getEvent();
            Integer classroomId = allocation.getClassroomId();

            overcrowdAccs.computeIfAbsent(new OvercrowdKey(event.getId(), classroomId),
                            k -> new OvercrowdAcc(event, classroomId))
                    .dates.add(occurrence.getDate());

            byClassroomAndDate.computeIfAbsent(new GroupKey(classroomId, occurrence.getDate()), k -> new ArrayList<>())
                    .add(allocation);
        }

        Map<OverlapKey, OverlapAcc> overlapAccs = computeOverlaps(byClassroomAndDate);

        // Batch único de eventos y aulas ajenos, reunidos desde ambas detecciones.
        Set<AcademicEvent> eventsToCompose = new LinkedHashSet<>();
        Set<Integer> classroomIdsToFetch = new LinkedHashSet<>();
        for (OvercrowdAcc acc : overcrowdAccs.values()) {
            eventsToCompose.add(acc.event);
            classroomIdsToFetch.add(acc.classroomId);
        }
        for (OverlapAcc acc : overlapAccs.values()) {
            eventsToCompose.add(acc.eventA);
            eventsToCompose.add(acc.eventB);
            classroomIdsToFetch.add(acc.classroomId);
        }

        Map<Long, AcademicEventResponseDto> eventDtoById = composeEventsById(eventsToCompose);
        Map<Integer, ClassroomResponseDto> classroomDtoById = classroomService.findByIds(classroomIdsToFetch).stream()
                .collect(Collectors.toMap(ClassroomResponseDto::id, c -> c));

        List<OvercrowdedAllocationDto> overcrowded = buildOvercrowded(overcrowdAccs, eventDtoById, classroomDtoById);
        List<ClassroomOverlapDto> overlaps = buildOverlaps(overlapAccs, eventDtoById, classroomDtoById);

        log.info("Problemas de asignación encontrados: unassigned={}, overcrowded={}, overlaps={}",
                unassigned.size(), overcrowded.size(), overlaps.size());

        return new AllocationProblemsResponseDto(unassigned, overcrowded, overlaps);
    }

    /**
     * Máximo {@code endDate} de los períodos académicos activos; si no hay período
     * activo o ninguno tiene {@code endDate}, {@code from + 6 meses}.
     */
    private LocalDate resolveDefaultTo(LocalDate from) {
        return academicPeriodService.findActive().stream()
                .map(AcademicPeriodResponseDto::endDate)
                .filter(endDate -> endDate != null)
                .max(Comparator.naturalOrder())
                .orElseGet(() -> from.plusMonths(6));
    }

    /**
     * Superposiciones: dentro de cada grupo (aula, fecha) ordena por hora de inicio y
     * barre con corte temprano (patrón {@code SolverServiceImpl.computeConflicts}) —
     * evita el producto cartesiano. Los pares se agregan por (eventoA, eventoB, aula)
     * acumulando todas las fechas en que chocan.
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

    /** Composición por lote de eventos ajenos, indexada por id para lookup O(1) al armar los DTOs finales. */
    private Map<Long, AcademicEventResponseDto> composeEventsById(Set<AcademicEvent> events) {
        List<AcademicEvent> eventList = new ArrayList<>(events);
        List<AcademicEventResponseDto> composed = academicEventComposer.compose(eventList);
        Map<Long, AcademicEventResponseDto> byId = new LinkedHashMap<>();
        for (int i = 0; i < eventList.size(); i++) {
            byId.put(eventList.get(i).getId(), composed.get(i));
        }
        return byId;
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
