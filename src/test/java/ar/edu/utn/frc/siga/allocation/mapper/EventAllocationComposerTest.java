package ar.edu.utn.frc.siga.allocation.mapper;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.UniqueEventAllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventAllocationComposer")
class EventAllocationComposerTest {

    @Mock
    private OccurrenceService occurrenceService;
    @Mock
    private AllocationRepository allocationRepository;
    @Mock
    private ClassroomService classroomService;

    private EventAllocationComposer composer;

    @BeforeEach
    void setUp() {
        composer = new EventAllocationComposer(occurrenceService, allocationRepository, classroomService);
        lenient().when(occurrenceService.findSlotsByEvents(any())).thenReturn(List.of());
        lenient().when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of());
        lenient().when(classroomService.findByIds(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("compose: calcula sobrecupo con la capacidad del aula asignada")
    void composeCalculaSobrecupo() {
        UniqueEventResponseDto event = uniqueEvent(3L, 40);
        ClassroomResponseDto classroom = classroom(5L, 30);
        AllocationResponseDto allocation = new AllocationResponseDto(
                1L, AllocationSource.MANUAL, null, "obs", null, null, classroom);

        UniqueEventAllocationResponseDto result = composer.compose(event, allocation);

        assertThat(result.overcrowdedBy()).isEqualTo(10);
        assertThat(result.classroom()).isEqualTo(classroom);
        assertThat(result.observation()).isEqualTo("obs");
    }

    @Test
    @DisplayName("compose: sin aula asignada, no calcula sobrecupo")
    void composeSinAula() {
        UniqueEventResponseDto event = uniqueEvent(3L, 40);
        AllocationResponseDto allocation = new AllocationResponseDto(
                1L, AllocationSource.MANUAL, null, null, null, null, null);

        UniqueEventAllocationResponseDto result = composer.compose(event, allocation);

        assertThat(result.classroom()).isNull();
        assertThat(result.overcrowdedBy()).isNull();
    }

    @Test
    @DisplayName("composeAll: evento sin ocurrencia asignada no tiene aula ni estado")
    void composeAllSinOcurrencia() {
        UniqueEventResponseDto event = uniqueEvent(3L, 20);
        when(occurrenceService.findSlotsByEvents(any())).thenReturn(List.of());

        List<UniqueEventAllocationResponseDto> result = composer.composeAll(List.of(event));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().status()).isNull();
        assertThat(result.getFirst().classroom()).isNull();
    }

    @Test
    @DisplayName("composeAll: junta evento, ocurrencia, asignación y aula por id")
    void composeAllJuntaTodo() {
        UniqueEventResponseDto event = uniqueEvent(3L, 40);
        OccurrenceSlotDto slot = new OccurrenceSlotDto(10L, 3L, LocalDate.of(2026, 3, 10),
                LocalTime.of(10, 0), LocalTime.of(11, 0), OccurrenceStatus.NEEDS_ROOM, 40);
        Allocation allocation = Allocation.builder()
                .id(1L).occurrenceId(10L).classroomId(5L).observation("obs").build();
        when(occurrenceService.findSlotsByEvents(any())).thenReturn(List.of(slot));
        when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of(allocation));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5L, 30)));

        List<UniqueEventAllocationResponseDto> result = composer.composeAll(List.of(event));

        assertThat(result).hasSize(1);
        UniqueEventAllocationResponseDto dto = result.getFirst();
        assertThat(dto.status()).isEqualTo(OccurrenceStatus.NEEDS_ROOM);
        assertThat(dto.classroom().id()).isEqualTo(5);
        assertThat(dto.overcrowdedBy()).isEqualTo(10);
        assertThat(dto.observation()).isEqualTo("obs");
    }

    @Test
    @DisplayName("composeAll: lista vacía no consulta ocurrencias")
    void composeAllListaVacia() {
        List<UniqueEventAllocationResponseDto> result = composer.composeAll(List.of());

        assertThat(result).isEmpty();
    }

    private UniqueEventResponseDto uniqueEvent(Long id, Integer enrolled) {
        return new UniqueEventResponseDto(id, EventType.UNIQUE_EVENT, UniqueEventKind.EXAMEN_FINAL, enrolled,
                LocalTime.of(10, 0), 60, LocalDate.of(2026, 3, 10), "evento especial", null, null);
    }

    private ClassroomResponseDto classroom(Long id, Integer capacity) {
        return new ClassroomResponseDto(id, id.intValue(), capacity, 1L, "Edificio 1", 1L, "Tipo");
    }
}
