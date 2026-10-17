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
import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerAllocation;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerEvent;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerOccupancy;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerRoom;
import ar.edu.utn.frc.siga.optimizer.service.OptimizerService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomSubjectPermissionDto;
import ar.edu.utn.frc.siga.space.model.ClassroomResources;
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
import static org.mockito.ArgumentMatchers.eq;
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
        ClassroomResponseDto outOfScope = new ClassroomResponseDto(6L, 6, 100, 9L, "Edificio Ajeno", 1L, "Tipo", null, true);
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
        SubjectResponseDto subject = new SubjectResponseDto(7L, 700, "Análisis", "1C", null, true, null);
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
        ClassroomResponseDto outOfScope = new ClassroomResponseDto(6L, 6, 100, 9L, "Edificio Ajeno", 1L, "Tipo", null, true);
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
                UniqueEventKind.PARCIAL, 20, LocalTime.of(18, 0), 120, LocalDate.of(2026, 3, 10), null, null, null, false, false, null, null, null);
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

    @Test
    @DisplayName("generate: único con comisión viaja con endTime = startTime + duración y commissionKey de la comisión")
    void generateUnicoCalculaEndTimeYCommissionKey() {
        LocalDate date = LocalDate.now().plusDays(5);
        UniqueEventResponseDto event = uniqueEvent(3L, 20, date, new CommissionResponseDto(77L, "CUR", null, null));
        stubUnique(event, uniqueSlot(30L, 3L, date, 1));

        engine.generate(Set.of(3L), 30);

        assertThat(capturedEvents()).singleElement().satisfies(e -> {
            assertThat(e.planningId()).isEqualTo("3");
            assertThat(e.startTime()).isEqualTo(LocalTime.of(18, 0));
            assertThat(e.endTime()).isEqualTo(LocalTime.of(20, 0));
            assertThat(e.commissionKey()).isEqualTo("77");
            assertThat(e.enrolled()).isEqualTo(20);
            assertThat(e.occurrenceDates()).containsExactly(date);
            assertThat(e.groupKey()).isNull();
        });
    }

    @Test
    @DisplayName("generate: único con 3 roomSlot genera 3 eventos con ids 3, 3:2 y 3:3, enrolled = ceil(total/3) y el mismo groupKey")
    void generateUnicoConTresRoomSlots() {
        LocalDate date = LocalDate.now().plusDays(5);
        UniqueEventResponseDto event = uniqueEvent(3L, 10, date, null);
        stubUnique(event, uniqueSlot(30L, 3L, date, 1), uniqueSlot(31L, 3L, date, 2), uniqueSlot(32L, 3L, date, 3));

        engine.generate(Set.of(3L), 30);

        List<OptimizerEvent> events = capturedEvents();
        assertThat(events).extracting(OptimizerEvent::planningId).containsExactly("3", "3:2", "3:3");
        assertThat(events).extracting(OptimizerEvent::enrolled).containsOnly(4);
        assertThat(events).extracting(OptimizerEvent::groupKey).containsOnly("3");
        assertThat(events).allSatisfy(e -> assertThat(e.occurrenceDates()).containsExactly(date));
    }

    @Test
    @DisplayName("generate: recurrente sin espejos viaja con planningId = id, groupKey null y enrolled completo")
    void generateRecurrenteSinEspejosNoCambia() {
        RecurringEventResponseDto event = recurringEvent(1L);
        LocalDate d1 = LocalDate.now().plusDays(2);
        LocalDate d2 = LocalDate.now().plusDays(9);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(occurrenceService.findSlotsByEvents(any(), any())).thenReturn(List.of(slot(10L, 1L, d2), slot(11L, 1L, d1)));
        stubOptimizer();

        engine.generate(Set.of(1L), 30);

        assertThat(capturedEvents()).singleElement().isEqualTo(new OptimizerEvent(
                "1", null, 30, LocalTime.of(8, 0), LocalTime.of(9, 30), Set.of(d1, d2), Set.of(), null));
    }

    @Test
    @DisplayName("generate: recurrente con espejo parcial divide por clave: el roomSlot 1 sigue completo y el espejo usa n=2")
    void generateEspejoParcialDivideSegunLaClave() {
        RecurringEventResponseDto event = recurringEvent(1L);
        LocalDate d1 = LocalDate.now().plusDays(2);
        LocalDate d2 = LocalDate.now().plusDays(9);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(occurrenceService.findSlotsByEvents(any(), any())).thenReturn(List.of(
                slot(10L, 1L, d1), slot(11L, 1L, d2), uniqueSlot(12L, 1L, d1, 2)));
        stubOptimizer();

        engine.generate(Set.of(1L), 30);

        List<OptimizerEvent> events = capturedEvents();
        assertThat(events).extracting(OptimizerEvent::planningId).containsExactly("1", "1:2");
        assertThat(events).extracting(OptimizerEvent::enrolled).containsExactly(30, 15);
        assertThat(events).extracting(OptimizerEvent::groupKey).containsOnly("1");
        assertThat(events.get(0).occurrenceDates()).containsExactlyInAnyOrder(d1, d2);
        assertThat(events.get(1).occurrenceDates()).containsExactly(d1);
    }

    @Test
    @DisplayName("generate: ocurrencias ROOM_RELEASED y pasadas quedan fuera de las fechas del solver")
    void generateExcluyeLiberadasYPasadas() {
        RecurringEventResponseDto event = recurringEvent(1L);
        LocalDate pending = LocalDate.now().plusDays(2);
        LocalDate released = LocalDate.now().plusDays(9);
        LocalDate past = LocalDate.now().minusDays(7);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(occurrenceService.findSlotsByEvents(any(), any())).thenReturn(List.of(
                slot(10L, 1L, pending),
                new OccurrenceSlotDto(11L, 1L, released, LocalTime.of(8, 0), LocalTime.of(9, 30),
                        OccurrenceStatus.ROOM_RELEASED, 30),
                slot(12L, 1L, past)));
        stubOptimizer();

        engine.generate(Set.of(1L), 30);

        assertThat(capturedEvents()).singleElement()
                .extracting(OptimizerEvent::occurrenceDates).isEqualTo(Set.of(pending));
    }

    @Test
    @DisplayName("generate: si todas las ocurrencias están liberadas lanza AllocationConflictException")
    void generateTodasLiberadasLanza() {
        RecurringEventResponseDto event = recurringEvent(1L);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(occurrenceService.findSlotsByEvents(any(), any())).thenReturn(List.of(
                new OccurrenceSlotDto(11L, 1L, LocalDate.now().plusDays(9), LocalTime.of(8, 0), LocalTime.of(9, 30),
                        OccurrenceStatus.ROOM_RELEASED, 30)));

        assertThatThrownBy(() -> engine.generate(Set.of(1L), 30))
                .isInstanceOf(AllocationConflictException.class);
    }

    @Test
    @DisplayName("loadInputs: con solo recurrentes la ocupación se pide desde startDate (aunque sea pasada) hasta endDate")
    void loadInputsRangoSoloRecurrentes() {
        RecurringEventResponseDto event = recurringEvent(1L);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(occurrenceService.findSlotsByEvents(any(), any())).thenReturn(List.of());

        engine.loadInputs(Set.of(1L));

        verify(occupancyService).findOccupancy(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 6, 30));
    }

    @Test
    @DisplayName("loadInputs: un único posterior al rango de los recurrentes extiende el fin del rango de ocupación")
    void loadInputsUnicoPosteriorExtiendeRango() {
        RecurringEventResponseDto recurring = recurringEvent(1L);
        UniqueEventResponseDto unique = uniqueEvent(3L, 20, LocalDate.of(2026, 12, 20), null);
        when(academicEventService.findByIds(any())).thenReturn(List.of(recurring, unique));
        when(occurrenceService.findSlotsByEvents(any(), any())).thenReturn(List.of());

        engine.loadInputs(Set.of(1L, 3L));

        verify(occupancyService).findOccupancy(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 12, 20));
    }

    @Test
    @DisplayName("loadInputs: un único anterior al rango de los recurrentes adelanta el inicio del rango de ocupación")
    void loadInputsUnicoAnteriorExtiendeRango() {
        RecurringEventResponseDto recurring = recurringEvent(1L);
        UniqueEventResponseDto unique = uniqueEvent(3L, 20, LocalDate.of(2025, 12, 1), null);
        when(academicEventService.findByIds(any())).thenReturn(List.of(recurring, unique));
        when(occurrenceService.findSlotsByEvents(any(), any())).thenReturn(List.of());

        engine.loadInputs(Set.of(1L, 3L));

        verify(occupancyService).findOccupancy(LocalDate.of(2025, 12, 1), LocalDate.of(2026, 6, 30));
    }

    @Test
    @DisplayName("loadInputs: el aula previa de un único se separa por roomSlot de la ocurrencia ocupada")
    void loadInputsAulaPreviaPorRoomSlot() {
        LocalDate date = LocalDate.now().plusDays(5);
        UniqueEventResponseDto event = uniqueEvent(3L, 20, date, null);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(occurrenceService.findSlotsByEvents(any(), any())).thenReturn(List.of());
        when(occupancyService.findOccupancy(any(), any())).thenReturn(List.of(
                new OccupiedSlot(5L, date, LocalTime.of(18, 0), LocalTime.of(20, 0), 3L, 900L, 30L),
                new OccupiedSlot(6L, date, LocalTime.of(18, 0), LocalTime.of(20, 0), 3L, 901L, 31L)));
        when(occurrenceService.findSlots(any())).thenReturn(List.of(
                uniqueSlot(30L, 3L, date, 1), uniqueSlot(31L, 3L, date, 2)));

        PreviewEngine.Inputs inputs = engine.loadInputs(Set.of(3L));

        assertThat(inputs.priorRoomByKey())
                .containsEntry(new PlanningKey(3L, 1), 5L)
                .containsEntry(new PlanningKey(3L, 2), 6L)
                .hasSize(2);
        assertThat(inputs.databaseOccupancy()).isEmpty();
    }

    @Test
    @DisplayName("generate: único con proyector y 20 PC en 2 aulas viaja con needsProjector y 10 PC por aula, y las aulas con sus recursos")
    void generateUnicoConRequisitosYDivisor() {
        LocalDate date = LocalDate.now().plusDays(5);
        UniqueEventResponseDto event = uniqueWithRequirements(3L, 40, date, true, true, 20);
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(5L, 50), classroom(6L, 50)));
        when(classroomService.findResourceAmounts(eq(ClassroomResources.PROJECTOR), any())).thenReturn(Map.of(5L, 1));
        when(classroomService.findResourceAmounts(eq(ClassroomResources.COMPUTERS), any())).thenReturn(Map.of(6L, 12));
        stubUnique(event, uniqueSlot(30L, 3L, date, 1), uniqueSlot(31L, 3L, date, 2));

        engine.generate(Set.of(3L), 30);

        assertThat(capturedEvents()).hasSize(2).allSatisfy(e -> {
            assertThat(e.needsProjector()).isTrue();
            assertThat(e.computersNeeded()).isEqualTo(10);
        });
        ArgumentCaptor<List<OptimizerRoom>> roomsCaptor = ArgumentCaptor.forClass(List.class);
        verify(optimizerService).optimize(any(), roomsCaptor.capture(), any(), anyInt());
        assertThat(roomsCaptor.getValue()).satisfiesExactly(
                r -> {
                    assertThat(r.id()).isEqualTo(5L);
                    assertThat(r.hasProjector()).isTrue();
                    assertThat(r.computers()).isZero();
                },
                r -> {
                    assertThat(r.id()).isEqualTo(6L);
                    assertThat(r.hasProjector()).isFalse();
                    assertThat(r.computers()).isEqualTo(12);
                });
    }

    @Test
    @DisplayName("generate: sin requisitos en el lote no consulta recursos y el evento viaja con false y 0")
    void generateSinRequisitosNoConsultaRecursos() {
        LocalDate date = LocalDate.now().plusDays(5);
        stubUnique(uniqueEvent(3L, 20, date, null), uniqueSlot(30L, 3L, date, 1));

        engine.generate(Set.of(3L), 30);

        verify(classroomService, never()).findResourceAmounts(any(), any());
        assertThat(capturedEvents()).singleElement().satisfies(e -> {
            assertThat(e.needsProjector()).isFalse();
            assertThat(e.computersNeeded()).isZero();
        });
    }

    @Test
    @DisplayName("suggest: único con proyector y 20 PC viaja con los requisitos completos (divisor 1)")
    void suggestUnicoConRequisitos() {
        LocalDate date = LocalDate.of(2026, 3, 2);
        UniqueEventResponseDto event = uniqueWithRequirements(3L, 20, date, true, true, 20);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(5L, 30)));
        when(classroomService.findResourceAmounts(eq(ClassroomResources.PROJECTOR), any())).thenReturn(Map.of(5L, 1));
        when(classroomService.findResourceAmounts(eq(ClassroomResources.COMPUTERS), any())).thenReturn(Map.of(5L, 25));
        when(buildingScopeResolver.scopeFor(Permission.ALLOCATION_WRITE)).thenReturn(BuildingScope.unrestricted());
        when(optimizerService.optimize(any(), any(), any(), anyInt()))
                .thenReturn(new OptimizationResult("prev_x", List.of(new OptimizerAllocation("3", 5L))));

        engine.suggest(3L, List.of(uniqueSlot(30L, 3L, date, 1)), Set.of(), 2);

        assertThat(capturedEvents()).singleElement().satisfies(e -> {
            assertThat(e.needsProjector()).isTrue();
            assertThat(e.computersNeeded()).isEqualTo(20);
        });
    }

    private void stubUnique(UniqueEventResponseDto event, OccurrenceSlotDto... slots) {
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(occurrenceService.findSlotsByEvents(any(), any())).thenReturn(List.of(slots));
        stubOptimizer();
    }

    private void stubOptimizer() {
        when(optimizerService.optimize(any(), any(), any(), anyInt()))
                .thenReturn(new OptimizationResult("prev_x", List.of()));
    }

    private List<OptimizerEvent> capturedEvents() {
        ArgumentCaptor<List<OptimizerEvent>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(optimizerService).optimize(eventsCaptor.capture(), any(), any(), anyInt());
        return eventsCaptor.getValue();
    }

    private UniqueEventResponseDto uniqueEvent(long id, int enrolled, LocalDate date, CommissionResponseDto commission) {
        return new UniqueEventResponseDto(id, EventType.UNIQUE_EVENT, UniqueEventKind.PARCIAL, enrolled,
                LocalTime.of(18, 0), 120, date, null, null, commission, false, false, null, null, null);
    }

    private UniqueEventResponseDto uniqueWithRequirements(long id, int enrolled, LocalDate date,
            boolean projector, boolean computers, Integer computerCount) {
        return new UniqueEventResponseDto(id, EventType.UNIQUE_EVENT, UniqueEventKind.PARCIAL, enrolled,
                LocalTime.of(18, 0), 120, date, null, null, null, projector, computers, computerCount, null, null);
    }

    private OccurrenceSlotDto uniqueSlot(Long occurrenceId, Long eventId, LocalDate date, int roomSlot) {
        return new OccurrenceSlotDto(occurrenceId, eventId, date, LocalTime.of(18, 0), LocalTime.of(20, 0),
                OccurrenceStatus.NEEDS_ROOM, 10, roomSlot);
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
        return new ClassroomResponseDto(id, id.intValue(), capacity, 1L, "Edificio 1", 1L, "Tipo", null, true);
    }
}
