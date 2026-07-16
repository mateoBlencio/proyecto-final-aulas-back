package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.request.AllocateFromDateRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocateOccurrenceRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.BatchReassignRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.mapper.AllocationComposer;
import ar.edu.utn.frc.siga.allocation.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator.AllocationCandidate;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementación de los intent methods de asignación manual/importada: valida contra el
 * estado actual de BD (occurrence asignable, aula existente, sin solapamiento) y aplica
 * cada operación (individual o batch) en una única transacción atómica.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AllocationServiceImpl implements AllocationService {

    private final AllocationRepository allocationRepository;
    private final OccurrenceRepository occurrenceRepository;
    private final AcademicEventRepository eventRepository;
    private final AllocationComposer composer;
    private final AllocationValidator validator;

    @Override
    @Transactional(readOnly = true)
    public AllocationResponseDto findById(Long allocationId) {
        Allocation allocation = allocationRepository.findByIdEager(allocationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Allocation", allocationId));
        return composer.compose(allocation);
    }

    /**
     * Asigna un aula a una occurrence puntual (source MANUAL). Rechaza si la occurrence ya
     * ocurrió, ya tiene asignación, está CANCELLED/SUSPENDED, o si el aula ya está ocupada
     * ese día/horario.
     */
    @Override
    @Transactional
    public AllocationResponseDto allocateManually(Long occurrenceId, AllocateOccurrenceRequestDto dto) {
        log.debug("Asignando ocurrencia={} a aula={}", occurrenceId, dto.classroomId());

        Occurrence occurrence = findOccurrence(occurrenceId);
        validator.validateNotPast(occurrence);
        validator.validateAssignable(occurrence);

        if (allocationRepository.findByOccurrence_Id(occurrenceId).isPresent()) {
            throw new AllocationConflictException("Occurrence " + occurrenceId + " already has an allocation.");
        }

        Integer classroomId = dto.classroomId();
        validator.validateClassroomsAvailable(Set.of(classroomId));
        validator.validateNoOverlap(List.of(new AllocationCandidate(occurrence, classroomId)));

        Allocation saved = allocationRepository.save(Allocation.builder()
                .occurrence(occurrence)
                .classroomId(classroomId)
                .source(AllocationSource.MANUAL)
                .createdAt(LocalDateTime.now())
                .observation(dto.observation())
                .build());

        occurrence.setStatus(OccurrenceStatus.ASSIGNED);
        occurrenceRepository.save(occurrence);

        log.info("Asignación creada: id={}, occurrenceId={}, classroomId={}", saved.getId(), occurrenceId, dto.classroomId());
        return composer.compose(saved);
    }

    /**
     * Cambia el aula de una asignación existente (source MANUAL). Rechaza si la occurrence
     * ya ocurrió o si el nuevo aula ya está ocupada ese día/horario.
     */
    @Override
    @Transactional
    public AllocationResponseDto reallocate(Long allocationId, AllocateOccurrenceRequestDto dto) {
        log.debug("Reasignando asignación={} a aula={}", allocationId, dto.classroomId());

        Allocation allocation = findAllocation(allocationId);
        validator.validateNotPast(allocation.getOccurrence());

        Integer classroomId = dto.classroomId();
        validator.validateClassroomsAvailable(Set.of(classroomId));
        validator.validateNoOverlap(List.of(new AllocationCandidate(allocation.getOccurrence(), classroomId)));

        allocation.setClassroomId(classroomId);
        allocation.setSource(AllocationSource.MANUAL);
        allocation.setObservation(dto.observation());

        Allocation saved = allocationRepository.save(allocation);
        log.info("Asignación reasignada: id={}, classroomId={}", allocationId, dto.classroomId());
        return composer.compose(saved);
    }

    /**
     * Reasigna varias asignaciones en una sola transacción atómica (source MANUAL): todos
     * los moves se resuelven y validan (contra BD y entre sí) antes de escribir nada; si
     * cualquiera choca o ya ocurrió, no se aplica ninguno.
     */
    @Override
    @Transactional
    public List<AllocationResponseDto> batchReallocate(BatchReassignRequestDto dto) {
        log.debug("batchReallocate: moves={}", dto.moves().size());

        // Primero se resuelven y validan TODOS los moves (contra BD y entre sí); nada
        // se escribe hasta que el lote completo esté libre de solapamientos.
        List<Allocation> allocations = new ArrayList<>();
        List<AllocationCandidate> candidates = new ArrayList<>();
        for (BatchReassignRequestDto.MoveDto move : dto.moves()) {
            Allocation allocation = findAllocation(move.allocationId());
            validator.validateNotPast(allocation.getOccurrence());
            Integer classroomId = move.classroomId();
            allocations.add(allocation);
            candidates.add(new AllocationCandidate(allocation.getOccurrence(), classroomId));
        }

        validator.validateClassroomsAvailable(
                candidates.stream()
                        .map(AllocationCandidate::classroomId)
                        .collect(Collectors.toSet())
        );
        validator.validateNoOverlap(candidates);

        List<AllocationResponseDto> results = new ArrayList<>();
        for (int i = 0; i < allocations.size(); i++) {
            Allocation allocation = allocations.get(i);
            allocation.setClassroomId(candidates.get(i).classroomId());
            allocation.setSource(AllocationSource.MANUAL);
            results.add(composer.compose(allocationRepository.save(allocation)));
        }
        log.info("batchReallocate completo: moved={}", results.size());
        return results;
    }

    /**
     * Cambia el aula de todas las occurrences futuras (fecha &ge; hoy) de un evento
     * recurrente (source MANUAL); las occurrences pasadas quedan intactas. Rechaza si el
     * evento no es recurrente o si ya finalizó (ninguna occurrence futura). Valida que
     * ninguna choque antes de aplicar el lote completo.
     */
    @Override
    @Transactional
    public List<AllocationResponseDto> reassignEvent(Long recurringEventId, AllocateOccurrenceRequestDto dto) {
        log.debug("reassignEvent: event={}, classroom={}", recurringEventId, dto.classroomId());

        AcademicEvent event = eventRepository.findById(recurringEventId)
                .orElseThrow(() -> ResourceNotFoundException.of("AcademicEvent", recurringEventId));

        if (!(Hibernate.unproxy(event) instanceof RecurringEvent)) {
            throw new AllocationConflictException("reassignEvent is only supported for recurring events");
        }

        List<Occurrence> occurrences = occurrenceRepository
                .findByEvent_IdAndDateGreaterThanEqual(recurringEventId, LocalDate.now());

        validator.validateEventNotFinished(occurrences);

        Integer classroomId = dto.classroomId();
        validator.validateClassroomsAvailable(Set.of(classroomId));

        validator.validateNoOverlap(
                occurrences.stream()
                        .map(o -> new AllocationCandidate(o, classroomId))
                        .toList()
        );

        List<Allocation> saved = allocateToOccurrences(
                occurrences, classroomId, dto.observation(), AllocationSource.MANUAL, true);
        List<AllocationResponseDto> results = composer.composeAll(saved);

        log.info("reassignEvent completo: event={}, allocated={}", recurringEventId, results.size());
        return results;
    }

    /**
     * Asigna un aula a todas las occurrences futuras (fecha &ge; hoy) de un evento
     * recurrente desde {@code fromDate}, salteando las que ya ocurrieron (source MANUAL).
     * Valida que ninguna choque antes de aplicar el lote completo; solo soportado para
     * eventos recurrentes.
     */
    @Override
    @Transactional
    public List<AllocationResponseDto> allocateManuallyFromDate(AllocateFromDateRequestDto dto) {
        log.debug("allocateManuallyFromDate: event={}, fromDate={}, classroom={}", dto.recurringEventId(), dto.fromDate(), dto.classroomId());

        AcademicEvent event = eventRepository.findById(dto.recurringEventId())
                .orElseThrow(() -> ResourceNotFoundException.of("AcademicEvent", dto.recurringEventId()));

        if (!(Hibernate.unproxy(event) instanceof RecurringEvent)) {
            throw new AllocationConflictException("allocateManuallyFromDate is only supported for recurring events");
        }

        Integer classroomId = dto.classroomId();
        validator.validateClassroomsAvailable(Set.of(classroomId));
        LocalDate effectiveFrom = dto.fromDate().isBefore(LocalDate.now()) ? LocalDate.now() : dto.fromDate();

        List<Occurrence> occurrences = occurrenceRepository
                .findByEvent_IdAndDateGreaterThanEqual(dto.recurringEventId(), effectiveFrom);

        validator.validateNoOverlap(
                occurrences.stream()
                        .map(o -> new AllocationCandidate(o, classroomId))
                        .toList()
        );

        List<Allocation> saved = allocateToOccurrences(
                occurrences, classroomId, dto.observation(), AllocationSource.MANUAL, true);
        List<AllocationResponseDto> results = composer.composeAll(saved);

        log.info("allocateManuallyFromDate completo: event={}, fromDate={}, allocated={}", dto.recurringEventId(), dto.fromDate(), results.size());
        return results;
    }

    /**
     * Igual que {@link #allocateManuallyFromDate}, pero para carga masiva desde Excel (source
     * IMPORTED): incluye occurrences pasadas (no se saltean) y no valida solapamiento previo,
     * ya que la importación reemplaza el estado existente por diseño. Devuelve solo la
     * cantidad aplicada: el caller (importación masiva) no usa el DTO compuesto.
     */
    @Override
    @Transactional
    public int importAllocationsFromDate(AllocateFromDateRequestDto dto) {
        log.debug("importAllocationsFromDate: event={}, fromDate={}, classroom={}", dto.recurringEventId(), dto.fromDate(), dto.classroomId());

        AcademicEvent event = eventRepository.findById(dto.recurringEventId())
                .orElseThrow(() -> ResourceNotFoundException.of("AcademicEvent", dto.recurringEventId()));

        if (!(Hibernate.unproxy(event) instanceof RecurringEvent)) {
            throw new AllocationConflictException("importAllocationsFromDate is only supported for recurring events");
        }

        Integer classroomId = dto.classroomId();
        validator.validateClassroomsAvailable(Set.of(classroomId));

        List<Occurrence> occurrences = occurrenceRepository
                .findByEvent_IdAndDateGreaterThanEqual(dto.recurringEventId(), dto.fromDate());

        List<Allocation> saved = allocateToOccurrences(
                occurrences, classroomId, dto.observation(), AllocationSource.IMPORTED, false);

        log.info("importAllocationsFromDate completo: event={}, fromDate={}, allocated={}", dto.recurringEventId(), dto.fromDate(), saved.size());
        return saved.size();
    }

    /**
     * Crea o actualiza (upsert por occurrence) la asignación de cada occurrence de la
     * lista al aula indicada, y la pasa a ASSIGNED. Las no-asignables (CANCELLED/SUSPENDED,
     * o pasadas cuando {@code skipPast}) se saltean por diseño, no son un fallo parcial.
     * Prefetch en batch (una sola query) de las allocations existentes para evitar N+1.
     */
    private List<Allocation> allocateToOccurrences(
            List<Occurrence> occurrences, Integer classroomId, String observation,
            AllocationSource source, boolean skipPast) {
        Map<Long, Allocation> existingByOccurrence = allocationRepository
                .findByOccurrence_IdIn(occurrences.stream().map(Occurrence::getId).toList())
                .stream().collect(Collectors.toMap(a -> a.getOccurrence().getId(), a -> a));

        List<Allocation> saved = new ArrayList<>();
        for (Occurrence occurrence : occurrences) {
            if (skipPast && occurrence.isPast()) continue;
            if (!validator.isAssignable(occurrence)) continue;

            Allocation existing = existingByOccurrence.get(occurrence.getId());
            Allocation allocation;
            if (existing != null) {
                existing.setClassroomId(classroomId);
                existing.setSource(source);
                existing.setObservation(observation);
                allocation = existing;
            } else {
                allocation = Allocation.builder()
                        .occurrence(occurrence)
                        .classroomId(classroomId)
                        .source(source)
                        .createdAt(LocalDateTime.now())
                        .observation(observation)
                        .build();
            }

            saved.add(allocationRepository.save(allocation));

            occurrence.setStatus(OccurrenceStatus.ASSIGNED);
            occurrenceRepository.save(occurrence);
        }
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AllocationResponseDto> findByDate(LocalDate date) {
        log.debug("findByDate: date={}", date);
        return composer.composeAll(allocationRepository.findByDateEager(date));
    }

    private Occurrence findOccurrence(Long id) {
        return occurrenceRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Ocurrencia no encontrada: id={}", id);
                    return ResourceNotFoundException.of("Occurrence", id);
                });
    }

    private Allocation findAllocation(Long id) {
        return allocationRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Asignación no encontrada: id={}", id);
                    return ResourceNotFoundException.of("Allocation", id);
                });
    }

}
