package ar.edu.utn.frc.siga.preview.service.impl;

import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.service.AllocationOccupancyService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import ar.edu.utn.frc.siga.optimizer.service.OptimizerService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PreviewEngine")
class PreviewEngineTest {

    @Mock
    private AcademicEventService academicEventService;
    @Mock
    private OccurrenceService occurrenceService;
    @Mock
    private ClassroomService classroomService;
    @Mock
    private AllocationOccupancyService occupancyService;
    @Mock
    private OptimizerService optimizerService;

    private PreviewEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PreviewEngine(academicEventService, occurrenceService, classroomService, occupancyService, optimizerService);
        lenient().when(classroomService.findAllAvailable()).thenReturn(List.of());
        lenient().when(occupancyService.findOccupancy(any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("generate: eventId inexistente lanza ResourceNotFoundException")
    void generateEventoInexistente() {
        when(academicEventService.findByIds(any())).thenReturn(List.of());

        assertThatThrownBy(() -> engine.generate(Set.of(99L), 30)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("generate: UniqueEvent rechazado, auto-preview solo soporta recurrentes")
    void generateRechazaUniqueEvent() {
        UniqueEventResponseDto unique = new UniqueEventResponseDto(3L, EventType.UNIQUE_EVENT,
                UniqueEventKind.PARCIAL, 20, LocalTime.of(18, 0), 120, LocalDate.of(2026, 3, 10), null, null, null);
        when(academicEventService.findByIds(any())).thenReturn(List.of(unique));

        assertThatThrownBy(() -> engine.generate(Set.of(3L), 30))
                .isInstanceOf(AllocationConflictException.class)
                .hasMessageContaining("recurrentes");
    }

    @Test
    @DisplayName("generate: evento sin ocurrencias pendientes lanza AllocationConflictException")
    void generateSinOcurrenciasPendientes() {
        RecurringEventResponseDto event = recurringEvent(1L);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(occurrenceService.findSlotsByEvents(any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> engine.generate(Set.of(1L), 30))
                .isInstanceOf(AllocationConflictException.class)
                .hasMessageContaining("ocurrencias pendientes");
    }

    @Test
    @DisplayName("generate: delega en el optimizador con los eventos, aulas y ocupación resueltos")
    void generateDelegaEnOptimizador() {
        RecurringEventResponseDto event = recurringEvent(1L);
        LocalDate date = LocalDate.now().plusDays(2);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(occurrenceService.findSlotsByEvents(any(), any())).thenReturn(
                List.of(new OccurrenceSlotDto(10L, 1L, date, LocalTime.of(8, 0), LocalTime.of(9, 30),
                        OccurrenceStatus.NEEDS_ROOM, 30)));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(5, 100)));
        when(optimizerService.optimize(any(), any(), any(), anyInt()))
                .thenReturn(new OptimizationResult("prev_x", List.of()));

        engine.generate(Set.of(1L), 45);

        ArgumentCaptor<Integer> timeLimitCaptor = ArgumentCaptor.forClass(Integer.class);
        org.mockito.Mockito.verify(optimizerService).optimize(any(), any(), any(), timeLimitCaptor.capture());
        assertThat(timeLimitCaptor.getValue()).isEqualTo(45);
    }

    @Test
    @DisplayName("loadInputs: endDate null usa startDate + 1 año como fin del rango de ocupación")
    void loadInputsEndDateNullUsaUnAnio() {
        RecurringEventResponseDto event = new RecurringEventResponseDto(1L, EventType.RECURRING, 30,
                LocalTime.of(8, 0), 90, DayOfWeek.MONDAY, LocalDate.of(2026, 3, 1), null, null, null);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(occurrenceService.findSlotsByEvents(any(), any())).thenReturn(List.of());

        engine.loadInputs(Set.of(1L));

        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        org.mockito.Mockito.verify(occupancyService).findOccupancy(any(), toCaptor.capture());
        assertThat(toCaptor.getValue()).isEqualTo(LocalDate.of(2027, 3, 1));
    }

    private RecurringEventResponseDto recurringEvent(long id) {
        return new RecurringEventResponseDto(id, EventType.RECURRING, 30, LocalTime.of(8, 0), 90,
                DayOfWeek.MONDAY, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 6, 30), null, null);
    }

    private ClassroomResponseDto classroom(Integer id, Integer capacity) {
        return new ClassroomResponseDto(id, "Aula " + id, 1, capacity, true, 1, "Edificio 1", 1, "Tipo");
    }
}
