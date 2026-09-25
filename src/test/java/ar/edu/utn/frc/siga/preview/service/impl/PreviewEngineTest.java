package ar.edu.utn.frc.siga.preview.service.impl;

import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.service.AllocationOccupancyService;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerAllocation;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerEvent;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerOccupancy;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerRoom;
import ar.edu.utn.frc.siga.optimizer.service.OptimizerService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomSubjectPermissionDto;
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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @Mock
    private BuildingScopeResolver buildingScopeResolver;

    private PreviewEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PreviewEngine(academicEventService, occurrenceService, classroomService, occupancyService,
                optimizerService, buildingScopeResolver);
        lenient().when(classroomService.findAllAvailable()).thenReturn(List.of());
        lenient().when(classroomService.findSubjectPermissions(any())).thenReturn(Map.of());
        lenient().when(occupancyService.findOccupancy(any(), any())).thenReturn(List.of());
        lenient().when(buildingScopeResolver.scopeFor(Permission.PREVIEW_RUN)).thenReturn(BuildingScope.unrestricted());
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
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(5L, 100)));
        when(optimizerService.optimize(any(), any(), any(), anyInt()))
                .thenReturn(new OptimizationResult("prev_x", List.of()));

        engine.generate(Set.of(1L), 45);

        ArgumentCaptor<Integer> timeLimitCaptor = ArgumentCaptor.forClass(Integer.class);
        org.mockito.Mockito.verify(optimizerService).optimize(any(), any(), any(), timeLimitCaptor.capture());
        assertThat(timeLimitCaptor.getValue()).isEqualTo(45);
    }

    @Test
    @DisplayName("loadInputs: aulas fuera del alcance del usuario no llegan al optimizador")
    void loadInputsFiltraAulasFueraDeAlcance() {
        RecurringEventResponseDto event = recurringEvent(1L);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(occurrenceService.findSlotsByEvents(any(), any())).thenReturn(List.of());
        ClassroomResponseDto outOfScope = new ClassroomResponseDto(6L, 6, 100, 9L, "Edificio Ajeno", 1L, "Tipo");
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(5L, 100), outOfScope));
        when(buildingScopeResolver.scopeFor(Permission.PREVIEW_RUN)).thenReturn(BuildingScope.of(Set.of(1L)));

        PreviewEngine.Inputs inputs = engine.loadInputs(Set.of(1L));

        assertThat(inputs.rooms()).extracting("id").containsExactly(5L);
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

    @Test
    @DisplayName("generate: la materia del evento y el permiso del aula viajan al optimizador")
    void generatePropagaPermisosYMateria() {
        SubjectResponseDto subject = new SubjectResponseDto(7L, 700, "Análisis", "1C", null);
        RecurringEventResponseDto event = new RecurringEventResponseDto(1L, EventType.RECURRING, 30,
                LocalTime.of(8, 0), 90, DayOfWeek.MONDAY, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 6, 30),
                subject, null);
        LocalDate date = LocalDate.now().plusDays(2);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(occurrenceService.findSlotsByEvents(any(), any())).thenReturn(
                List.of(new OccurrenceSlotDto(10L, 1L, date, LocalTime.of(8, 0), LocalTime.of(9, 30),
                        OccurrenceStatus.NEEDS_ROOM, 30)));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(5L, 100), classroom(6L, 100)));
        when(classroomService.findSubjectPermissions(any())).thenReturn(Map.of(
                5L, new ClassroomSubjectPermissionDto(5L, false, Set.of(7L)),
                6L, new ClassroomSubjectPermissionDto(6L, false, Set.of())));
        when(optimizerService.optimize(any(), any(), any(), anyInt()))
                .thenReturn(new OptimizationResult("prev_x", List.of()));

        engine.generate(Set.of(1L), 30);

        ArgumentCaptor<List<OptimizerEvent>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<OptimizerRoom>> roomsCaptor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(optimizerService)
                .optimize(eventsCaptor.capture(), roomsCaptor.capture(), any(), anyInt());

        assertThat(eventsCaptor.getValue()).singleElement()
                .extracting(OptimizerEvent::subjectIds).isEqualTo(Set.of(7L));
        assertThat(roomsCaptor.getValue())
                .filteredOn(r -> r.id().equals(5L)).singleElement()
                .satisfies(r -> assertThat(r.permits(Set.of(7L))).isTrue());
        assertThat(roomsCaptor.getValue())
                .filteredOn(r -> r.id().equals(6L)).singleElement()
                .satisfies(r -> assertThat(r.permits(Set.of(7L))).isFalse());
    }

    @Test
    @DisplayName("suggest: el OptimizerEvent capturado tiene exactamente las fechas del rango pedido")
    void suggestCapturaExactamenteLasFechasDelRango() {
        RecurringEventResponseDto event = recurringEvent(1L);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(5L, 100)));
        when(buildingScopeResolver.scopeFor(Permission.ALLOCATION_WRITE)).thenReturn(BuildingScope.unrestricted());
        List<OccurrenceSlotDto> slots = List.of(
                slot(10L, 1L, LocalDate.of(2026, 3, 2)),
                slot(11L, 1L, LocalDate.of(2026, 3, 9)),
                slot(12L, 1L, LocalDate.of(2026, 3, 16)));
        when(optimizerService.optimize(any(), any(), any(), anyInt()))
                .thenReturn(new OptimizationResult("prev_x", List.of(new OptimizerAllocation("1", 5L))));

        engine.suggest(1L, slots, Set.of(), 2);

        ArgumentCaptor<List<OptimizerEvent>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(optimizerService).optimize(eventsCaptor.capture(), any(), any(), anyInt());
        assertThat(eventsCaptor.getValue()).singleElement()
                .extracting(OptimizerEvent::occurrenceDates)
                .isEqualTo(Set.of(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 16)));
    }

    @Test
    @DisplayName("suggest: la ocupación capturada excluye slots del propio evento e incluye los de otro evento")
    void suggestOcupacionExcluyeSlotsDelPropioEventoIncluyeOtroEvento() {
        RecurringEventResponseDto event = recurringEvent(1L);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(5L, 100)));
        when(buildingScopeResolver.scopeFor(Permission.ALLOCATION_WRITE)).thenReturn(BuildingScope.unrestricted());
        LocalDate date = LocalDate.of(2026, 3, 2);
        List<OccurrenceSlotDto> slots = List.of(slot(10L, 1L, date));
        OccupiedSlot ownEventSlot = new OccupiedSlot(9L, date, LocalTime.of(8, 0), LocalTime.of(9, 30), 1L, 100L);
        OccupiedSlot otherEventSlot = new OccupiedSlot(6L, date, LocalTime.of(8, 0), LocalTime.of(9, 30), 2L, 101L);
        when(occupancyService.findOccupancy(date, date)).thenReturn(List.of(ownEventSlot, otherEventSlot));
        when(optimizerService.optimize(any(), any(), any(), anyInt()))
                .thenReturn(new OptimizationResult("prev_x", List.of(new OptimizerAllocation("1", 5L))));

        engine.suggest(1L, slots, Set.of(), 2);

        ArgumentCaptor<List<OptimizerOccupancy>> occupancyCaptor = ArgumentCaptor.forClass(List.class);
        verify(optimizerService).optimize(any(), any(), occupancyCaptor.capture(), anyInt());
        assertThat(occupancyCaptor.getValue()).extracting(OptimizerOccupancy::classroomId).containsExactly(6L);
    }

    @Test
    @DisplayName("suggest: el evento capturado abarca desde el inicio más temprano hasta el fin más tardío de los slots")
    void suggestEventoAbarcaUnionDeHorarios() {
        RecurringEventResponseDto event = recurringEvent(1L);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(5L, 100)));
        when(buildingScopeResolver.scopeFor(Permission.ALLOCATION_WRITE)).thenReturn(BuildingScope.unrestricted());
        LocalDate d1 = LocalDate.of(2026, 3, 2);
        LocalDate d2 = LocalDate.of(2026, 3, 9);
        List<OccurrenceSlotDto> slots = List.of(
                new OccurrenceSlotDto(10L, 1L, d1, LocalTime.of(10, 0), LocalTime.of(12, 0),
                        OccurrenceStatus.NEEDS_ROOM, 30),
                new OccurrenceSlotDto(11L, 1L, d2, LocalTime.of(11, 0), LocalTime.of(13, 0),
                        OccurrenceStatus.NEEDS_ROOM, 30));
        when(optimizerService.optimize(any(), any(), any(), anyInt()))
                .thenReturn(new OptimizationResult("prev_x", List.of(new OptimizerAllocation("1", 5L))));

        engine.suggest(1L, slots, Set.of(), 2);

        ArgumentCaptor<List<OptimizerEvent>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(optimizerService).optimize(eventsCaptor.capture(), any(), any(), anyInt());
        OptimizerEvent captured = eventsCaptor.getValue().getFirst();
        assertThat(captured.startTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(captured.endTime()).isEqualTo(LocalTime.of(13, 0));
    }

    @Test
    @DisplayName("suggest: aula excluida por el pedido y aula fuera de alcance ALLOCATION_WRITE no llegan al optimizador")
    void suggestExcluyeAulaEnListaYFueraDeAlcance() {
        RecurringEventResponseDto event = recurringEvent(1L);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        ClassroomResponseDto excluded = classroom(5L, 100);
        ClassroomResponseDto outOfScope = new ClassroomResponseDto(6L, 6, 100, 9L, "Edificio Ajeno", 1L, "Tipo");
        ClassroomResponseDto candidate = classroom(7L, 100);
        when(classroomService.findAllAvailable()).thenReturn(List.of(excluded, outOfScope, candidate));
        when(buildingScopeResolver.scopeFor(Permission.ALLOCATION_WRITE)).thenReturn(BuildingScope.of(Set.of(1L)));
        List<OccurrenceSlotDto> slots = List.of(slot(10L, 1L, LocalDate.of(2026, 3, 2)));
        when(optimizerService.optimize(any(), any(), any(), anyInt()))
                .thenReturn(new OptimizationResult("prev_x", List.of(new OptimizerAllocation("1", 7L))));

        engine.suggest(1L, slots, Set.of(5L), 2);

        ArgumentCaptor<List<OptimizerRoom>> roomsCaptor = ArgumentCaptor.forClass(List.class);
        verify(optimizerService).optimize(any(), roomsCaptor.capture(), any(), anyInt());
        assertThat(roomsCaptor.getValue()).extracting(OptimizerRoom::id).containsExactly(7L);
    }

    @Test
    @DisplayName("suggest: todas las aulas excluidas deja classroom null y nunca llama al optimizador")
    void suggestTodasLasAulasExcluidasNoLlamaAlOptimizador() {
        RecurringEventResponseDto event = recurringEvent(1L);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(5L, 100)));
        when(buildingScopeResolver.scopeFor(Permission.ALLOCATION_WRITE)).thenReturn(BuildingScope.unrestricted());
        List<OccurrenceSlotDto> slots = List.of(slot(10L, 1L, LocalDate.of(2026, 3, 2)));

        PreviewEngine.Suggestion suggestion = engine.suggest(1L, slots, Set.of(5L), 2);

        assertThat(suggestion.classroom()).isNull();
        assertThat(suggestion.overcrowdedBy()).isZero();
        verify(optimizerService, never()).optimize(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("suggest: aula con sobrecupo calcula overcrowdedBy = enrolled - capacity")
    void suggestOvercrowdedBy() {
        RecurringEventResponseDto event = recurringEvent(1L);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(5L, 30)));
        when(buildingScopeResolver.scopeFor(Permission.ALLOCATION_WRITE)).thenReturn(BuildingScope.unrestricted());
        List<OccurrenceSlotDto> slots = List.of(
                new OccurrenceSlotDto(10L, 1L, LocalDate.of(2026, 3, 2), LocalTime.of(8, 0), LocalTime.of(9, 30),
                        OccurrenceStatus.NEEDS_ROOM, 40));
        when(optimizerService.optimize(any(), any(), any(), anyInt()))
                .thenReturn(new OptimizationResult("prev_x", List.of(new OptimizerAllocation("1", 5L))));

        PreviewEngine.Suggestion suggestion = engine.suggest(1L, slots, Set.of(), 2);

        assertThat(suggestion.classroom().id()).isEqualTo(5L);
        assertThat(suggestion.overcrowdedBy()).isEqualTo(10);
    }

    @Test
    @DisplayName("suggest: evento único no lanza y viaja con commissionKey null")
    void suggestEventoUnicoCommissionKeyNull() {
        UniqueEventResponseDto event = new UniqueEventResponseDto(3L, EventType.UNIQUE_EVENT,
                UniqueEventKind.PARCIAL, 20, LocalTime.of(18, 0), 120, LocalDate.of(2026, 3, 10), null, null, null);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(5L, 100)));
        when(buildingScopeResolver.scopeFor(Permission.ALLOCATION_WRITE)).thenReturn(BuildingScope.unrestricted());
        List<OccurrenceSlotDto> slots = List.of(slot(20L, 3L, LocalDate.of(2026, 3, 10)));
        when(optimizerService.optimize(any(), any(), any(), anyInt()))
                .thenReturn(new OptimizationResult("prev_x", List.of(new OptimizerAllocation("3", 5L))));

        PreviewEngine.Suggestion suggestion = engine.suggest(3L, slots, Set.of(), 2);

        assertThat(suggestion.classroom()).isNotNull();
        ArgumentCaptor<List<OptimizerEvent>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(optimizerService).optimize(eventsCaptor.capture(), any(), any(), anyInt());
        assertThat(eventsCaptor.getValue()).singleElement().extracting(OptimizerEvent::commissionKey).isNull();
    }

    private OccurrenceSlotDto slot(Long occurrenceId, Long eventId, LocalDate date) {
        return new OccurrenceSlotDto(occurrenceId, eventId, date, LocalTime.of(8, 0), LocalTime.of(9, 30),
                OccurrenceStatus.NEEDS_ROOM, 30);
    }

    private RecurringEventResponseDto recurringEvent(long id) {
        return new RecurringEventResponseDto(id, EventType.RECURRING, 30, LocalTime.of(8, 0), 90,
                DayOfWeek.MONDAY, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 6, 30), null, null);
    }

    private ClassroomResponseDto classroom(Long id, Integer capacity) {
        return new ClassroomResponseDto(id, id.intValue(), capacity, 1L, "Edificio 1", 1L, "Tipo");
    }
}
