package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.request.AllocateOccurrenceRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.CreateUniqueEventAllocationRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.UpdateUniqueEventAllocationRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.UniqueEventAllocationResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.mapper.EventAllocationComposer;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UniqueEventAllocationServiceImpl")
class UniqueEventAllocationServiceImplTest {

    @Mock
    private AcademicEventService academicEventService;
    @Mock
    private AllocationService allocationService;
    @Mock
    private AllocationRepository allocationRepository;
    @Mock
    private EventAllocationComposer composer;

    private UniqueEventAllocationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UniqueEventAllocationServiceImpl(academicEventService, allocationService, allocationRepository, composer);
    }

    @Test
    @DisplayName("createUniqueEvent: crea el evento bare y asigna el aula indicada")
    void createUniqueEventFeliz() {
        CreateUniqueEventAllocationRequestDto dto = new CreateUniqueEventAllocationRequestDto(
                UniqueEventKind.EXAMEN_FINAL, 1L, 1L, LocalDate.of(2026, 3, 10),
                LocalTime.of(10, 0), 60, 20, 5, "obs");
        UniqueEventResponseDto created = dummyUniqueResponseDto(3L);
        when(academicEventService.createUniqueEvent(any())).thenReturn(created);
        when(academicEventService.findOccurrencesByEventId(3L)).thenReturn(List.of(dummyOccurrenceDto(10L, 3L)));
        AllocationResponseDto allocation = dummyAllocationResponseDto();
        when(allocationService.allocateManually(eq(10L), any())).thenReturn(allocation);
        UniqueEventAllocationResponseDto expected = new UniqueEventAllocationResponseDto(created, OccurrenceStatus.ASSIGNED, null, 0, null);
        when(composer.compose(created, allocation)).thenReturn(expected);

        UniqueEventAllocationResponseDto result = service.createUniqueEvent(dto);

        assertThat(result).isEqualTo(expected);
        verify(allocationService).allocateManually(10L, new AllocateOccurrenceRequestDto(dto.classroomId(), null));
    }

    @Test
    @DisplayName("createUniqueEvent: aula no disponible/solapada → la excepción de allocateManually se propaga (nada queda comiteado a medias)")
    void createUniqueEventAulaNoDisponiblePropagaExcepcion() {
        CreateUniqueEventAllocationRequestDto dto = new CreateUniqueEventAllocationRequestDto(
                UniqueEventKind.OTRO, null, null, LocalDate.of(2026, 3, 10),
                LocalTime.of(10, 0), 60, 20, 5, null);
        UniqueEventResponseDto created = dummyUniqueResponseDto(3L);
        when(academicEventService.createUniqueEvent(any())).thenReturn(created);
        when(academicEventService.findOccurrencesByEventId(3L)).thenReturn(List.of(dummyOccurrenceDto(10L, 3L)));
        doThrow(new AllocationConflictException("aula ocupada")).when(allocationService).allocateManually(any(), any());

        assertThatThrownBy(() -> service.createUniqueEvent(dto))
                .isInstanceOf(AllocationConflictException.class);
    }

    @Test
    @DisplayName("updateUniqueEvent: ya tiene allocation → reallocate (no allocateManually)")
    void updateUniqueEventConAllocationExistenteReasigna() {
        UpdateUniqueEventAllocationRequestDto dto = updateDto();
        UniqueEventResponseDto updated = dummyUniqueResponseDto(3L);
        when(academicEventService.updateUniqueEvent(eq(3L), any())).thenReturn(updated);
        when(academicEventService.findOccurrencesByEventId(3L)).thenReturn(List.of(dummyOccurrenceDto(10L, 3L)));
        Allocation existing = Allocation.builder().id(50L).occurrenceId(10L).classroomId(3).build();
        when(allocationRepository.findByOccurrenceId(10L)).thenReturn(Optional.of(existing));
        AllocationResponseDto allocation = dummyAllocationResponseDto();
        when(allocationService.reallocate(eq(50L), any())).thenReturn(allocation);
        UniqueEventAllocationResponseDto expected = new UniqueEventAllocationResponseDto(updated, OccurrenceStatus.ASSIGNED, null, 0, null);
        when(composer.compose(updated, allocation)).thenReturn(expected);

        UniqueEventAllocationResponseDto result = service.updateUniqueEvent(3L, dto);

        assertThat(result).isEqualTo(expected);
        verify(allocationService).reallocate(50L, new AllocateOccurrenceRequestDto(dto.classroomId(), null));
        verify(allocationService, never()).allocateManually(any(), any());
    }

    @Test
    @DisplayName("updateUniqueEvent: sin allocation previa → allocateManually (no reallocate)")
    void updateUniqueEventSinAllocationAsignaManualmente() {
        UpdateUniqueEventAllocationRequestDto dto = updateDto();
        UniqueEventResponseDto updated = dummyUniqueResponseDto(3L);
        when(academicEventService.updateUniqueEvent(eq(3L), any())).thenReturn(updated);
        when(academicEventService.findOccurrencesByEventId(3L)).thenReturn(List.of(dummyOccurrenceDto(10L, 3L)));
        when(allocationRepository.findByOccurrenceId(10L)).thenReturn(Optional.empty());
        AllocationResponseDto allocation = dummyAllocationResponseDto();
        when(allocationService.allocateManually(eq(10L), any())).thenReturn(allocation);
        UniqueEventAllocationResponseDto expected = new UniqueEventAllocationResponseDto(updated, OccurrenceStatus.ASSIGNED, null, 0, null);
        when(composer.compose(updated, allocation)).thenReturn(expected);

        service.updateUniqueEvent(3L, dto);

        verify(allocationService).allocateManually(10L, new AllocateOccurrenceRequestDto(dto.classroomId(), null));
        verify(allocationService, never()).reallocate(any(), any());
    }

    @Test
    @DisplayName("findAll: delega en el composer sobre los eventos únicos bare")
    void findAllDelegaEnComposer() {
        when(academicEventService.findUniqueEvents()).thenReturn(List.of(dummyUniqueResponseDto(3L)));
        when(composer.composeAll(any())).thenReturn(
                List.of(new UniqueEventAllocationResponseDto(dummyUniqueResponseDto(3L), OccurrenceStatus.ASSIGNED, null, 0, null)));

        List<UniqueEventAllocationResponseDto> result = service.findAll();

        assertThat(result).hasSize(1);
    }

    private UniqueEventResponseDto dummyUniqueResponseDto(Long id) {
        return new UniqueEventResponseDto(id, EventType.UNIQUE_EVENT, UniqueEventKind.EXAMEN_FINAL, 20,
                LocalTime.of(10, 0), 60, LocalDate.of(2026, 3, 10), "evento especial", null, null);
    }

    private OccurrenceResponseDto dummyOccurrenceDto(Long id, Long eventId) {
        return new OccurrenceResponseDto(id, eventId, LocalDate.of(2026, 3, 10), OccurrenceStatus.SCHEDULED,
                LocalTime.of(10, 0), LocalTime.of(11, 0));
    }

    private AllocationResponseDto dummyAllocationResponseDto() {
        return new AllocationResponseDto(1L, AllocationSource.MANUAL, LocalDateTime.now(), null, null, null, null);
    }

    private UpdateUniqueEventAllocationRequestDto updateDto() {
        return new UpdateUniqueEventAllocationRequestDto(
                UniqueEventKind.PARCIAL, 1L, 1L, LocalDate.of(2026, 3, 15),
                LocalTime.of(11, 0), 90, 25, 7, "descripcion actualizada");
    }
}
