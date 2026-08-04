package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.request.AllocateOccurrenceRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.CreateUniqueEventAllocationRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.UpdateUniqueEventAllocationRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.UniqueEventAllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.events.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.allocation.events.dto.request.UpdateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.allocation.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.events.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.allocation.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.allocation.mapper.EventAllocationComposer;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.UniqueEventAllocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementación de la orquesta evento-único + aula: delega la parte de evento en
 * {@link AcademicEventService} (bare, sin aula) y la de asignación en {@link AllocationService},
 * ambas dentro de la misma transacción (mismo datasource, propagación REQUIRED por defecto).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UniqueEventAllocationServiceImpl implements UniqueEventAllocationService {

    private final AcademicEventService academicEventService;
    private final AllocationService allocationService;
    private final AllocationRepository allocationRepository;
    private final EventAllocationComposer composer;

    @Override
    @Transactional(readOnly = true)
    public List<UniqueEventAllocationResponseDto> findAll() {
        return composer.composeAll(academicEventService.findUniqueEvents());
    }

    @Override
    @Transactional
    public UniqueEventAllocationResponseDto createUniqueEvent(CreateUniqueEventAllocationRequestDto dto) {
        log.debug("Creando evento único con aula: eventType={}, date={}, classroomId={}",
                dto.eventType(), dto.date(), dto.classroomId());

        AcademicEventResponseDto created = academicEventService.createUniqueEvent(new CreateUniqueEventRequestDto(
                dto.eventType(), dto.subjectId(), dto.commissionId(), dto.date(), dto.startTime(),
                dto.durationMinutes(), dto.enrolled(), dto.description()));
        Long occurrenceId = firstOccurrenceId(created.id());

        AllocationResponseDto allocation = allocationService.allocateManually(occurrenceId,
                new AllocateOccurrenceRequestDto(dto.classroomId(), null));

        log.info("Evento único con aula creado: id={}, classroomId={}", created.id(), dto.classroomId());
        return composer.compose((UniqueEventResponseDto) created, allocation);
    }

    @Override
    @Transactional
    public UniqueEventAllocationResponseDto updateUniqueEvent(Long id, UpdateUniqueEventAllocationRequestDto dto) {
        log.debug("Actualizando evento único con aula: id={}, classroomId={}", id, dto.classroomId());

        AcademicEventResponseDto updated = academicEventService.updateUniqueEvent(id, new UpdateUniqueEventRequestDto(
                dto.eventType(), dto.subjectId(), dto.commissionId(), dto.date(), dto.startTime(),
                dto.durationMinutes(), dto.enrolled(), dto.description()));
        Long occurrenceId = firstOccurrenceId(id);

        AllocateOccurrenceRequestDto allocationDto = new AllocateOccurrenceRequestDto(dto.classroomId(), null);
        AllocationResponseDto allocation = allocationRepository.findByOccurrenceId(occurrenceId)
                .map(existing -> allocationService.reallocate(existing.getId(), allocationDto))
                .orElseGet(() -> allocationService.allocateManually(occurrenceId, allocationDto));

        log.info("Evento único con aula actualizado: id={}", id);
        return composer.compose((UniqueEventResponseDto) updated, allocation);
    }

    private Long firstOccurrenceId(Long eventId) {
        return academicEventService.findOccurrencesByEventId(eventId).getFirst().id();
    }
}
