package ar.edu.utn.frc.siga.allocation.events.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.allocation.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.events.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.events.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.events.model.UniqueEvent;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compone el DTO de un evento académico resolviendo datos ajenos a la entidad: materia y
 * comisión (ambos subtipos) y, para únicos, además estado/aula/sobrecupo de su única ocurrencia.
 */
@Component
@RequiredArgsConstructor
public class AcademicEventComposer {

    private final AcademicEventMapper mapper;
    private final SubjectService subjectService;
    private final CommissionService commissionService;
    private final OccurrenceRepository occurrenceRepository;
    private final AllocationRepository allocationRepository;
    private final ClassroomService classroomService;

    /** Composición de un único evento (delega en el batch con una lista de un elemento). */
    public AcademicEventResponseDto compose(AcademicEvent event) {
        return compose(List.of(event)).getFirst();
    }

    /** Composición por lote indexada por id de evento, para lookups posteriores por id. */
    public Map<Long, AcademicEventResponseDto> composeById(List<? extends AcademicEvent> events) {
        List<AcademicEventResponseDto> composed = compose(events);
        Map<Long, AcademicEventResponseDto> byId = new LinkedHashMap<>();
        for (int i = 0; i < events.size(); i++) {
            byId.put(events.get(i).getId(), composed.get(i));
        }
        return byId;
    }

    /** Composición por lote: prefetch de materias/comisiones/ocupación distintas, sin N+1. */
    public List<AcademicEventResponseDto> compose(Collection<? extends AcademicEvent> events) {
        List<AcademicEvent> realEvents = events.stream()
                .map(e -> (AcademicEvent) Hibernate.unproxy(e))
                .toList();

        Set<Long> subjectIds = new LinkedHashSet<>();
        Set<Long> commissionIds = new LinkedHashSet<>();
        List<UniqueEvent> uniqueEvents = new ArrayList<>();
        for (AcademicEvent event : realEvents) {
            if (event.getSubjectId() != null) {
                subjectIds.add(event.getSubjectId());
            }
            if (event.getCommissionId() != null) {
                commissionIds.add(event.getCommissionId());
            }
            if (event instanceof UniqueEvent u) {
                uniqueEvents.add(u);
            }
        }

        Map<Long, SubjectResponseDto> subjectsById = Maps.byId(subjectService.findByIds(subjectIds), SubjectResponseDto::id);
        Map<Long, CommissionResponseDto> commissionsById = Maps.byId(commissionService.findByIds(commissionIds), CommissionResponseDto::id);
        UniqueEventOccupancy occupancy = loadUniqueEventOccupancy(uniqueEvents);

        List<AcademicEventResponseDto> result = new ArrayList<>(realEvents.size());
        for (AcademicEvent event : realEvents) {
            SubjectResponseDto subject = event.getSubjectId() != null ? subjectsById.get(event.getSubjectId()) : null;
            CommissionResponseDto commission = event.getCommissionId() != null ? commissionsById.get(event.getCommissionId()) : null;
            if (event instanceof RecurringEvent r) {
                result.add(mapper.toDto(r, subject, commission));
            } else {
                result.add(composeUnique((UniqueEvent) event, subject, commission, occupancy));
            }
        }
        return result;
    }

    private AcademicEventResponseDto composeUnique(UniqueEvent event, SubjectResponseDto subject,
            CommissionResponseDto commission, UniqueEventOccupancy occupancy) {
        Occurrence occurrence = occupancy.occurrenceByEventId().get(event.getId());
        Allocation allocation = occurrence != null ? occupancy.allocationByOccurrenceId().get(occurrence.getId()) : null;
        ClassroomResponseDto classroom = allocation != null ? occupancy.classroomById().get(allocation.getClassroomId()) : null;
        Integer overcrowdedBy = (classroom != null && classroom.capacity() != null && event.getEnrolled() != null)
                ? Math.max(0, event.getEnrolled() - classroom.capacity())
                : null;
        String observation = allocation != null ? allocation.getObservation() : null;
        OccurrenceStatus status = occurrence != null ? occurrence.getStatus() : null;
        return mapper.toDto(event, subject, commission, status, classroom, overcrowdedBy, observation);
    }

    private record UniqueEventOccupancy(Map<Long, Occurrence> occurrenceByEventId,
            Map<Long, Allocation> allocationByOccurrenceId, Map<Integer, ClassroomResponseDto> classroomById) {
    }

    /** Carga, en batch, la única occurrence de cada evento, su allocation (si tiene) y el aula. */
    private UniqueEventOccupancy loadUniqueEventOccupancy(List<UniqueEvent> uniqueEvents) {
        if (uniqueEvents.isEmpty()) {
            return new UniqueEventOccupancy(Map.of(), Map.of(), Map.of());
        }

        Set<Long> eventIds = uniqueEvents.stream().map(UniqueEvent::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<Occurrence> occurrences = occurrenceRepository.findByEvent_IdIn(eventIds);
        Map<Long, Occurrence> occurrenceByEventId = new LinkedHashMap<>();
        for (Occurrence occurrence : occurrences) {
            occurrenceByEventId.put(occurrence.getEvent().getId(), occurrence);
        }

        Set<Long> occurrenceIds = occurrences.stream().map(Occurrence::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<Allocation> allocations = occurrenceIds.isEmpty() ? List.of() : allocationRepository.findByOccurrence_IdIn(occurrenceIds);
        Map<Long, Allocation> allocationByOccurrenceId = new LinkedHashMap<>();
        for (Allocation allocation : allocations) {
            allocationByOccurrenceId.put(allocation.getOccurrence().getId(), allocation);
        }

        Set<Integer> classroomIds = allocations.stream().map(Allocation::getClassroomId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Integer, ClassroomResponseDto> classroomById = Maps.byId(classroomService.findByIds(classroomIds), ClassroomResponseDto::id);

        return new UniqueEventOccupancy(occurrenceByEventId, allocationByOccurrenceId, classroomById);
    }
}
