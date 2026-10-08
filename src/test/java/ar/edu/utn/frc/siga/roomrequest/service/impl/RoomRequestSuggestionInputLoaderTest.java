package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.allocation.service.AllocationOccupancyService;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerOccupancy;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerRoom;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestTransitionException;
import ar.edu.utn.frc.siga.roomrequest.exception.RoomRequestAlreadyNotifiedException;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestAccessControl;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestAccessControl.Action;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestTransitionValidator;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomSubjectPermissionDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomRequestSuggestionInputLoaderTest {

    private static final Long ITEM_ID = 7L;
    private static final String ACTOR = "sub@utn.edu.ar";
    private static final LocalDate DATE = LocalDate.of(2030, 3, 4);

    @Mock RoomRequestItemRepository itemRepository;
    @Mock RoomRequestTransitionValidator transitionValidator;
    @Mock RoomRequestAccessControl accessControl;
    @Mock RoomRequestCandidateResolver candidateResolver;
    @Mock RoomRequestOccurrenceResolver occurrenceResolver;
    @Mock ClassroomService classroomService;
    @Mock AllocationOccupancyService allocationOccupancyService;
    @Mock AcademicEventService academicEventService;

    private RoomRequestSuggestionInputLoader loader;

    @BeforeEach
    void setUp() {
        loader = new RoomRequestSuggestionInputLoader(itemRepository, transitionValidator, accessControl,
                candidateResolver, occurrenceResolver, classroomService, allocationOccupancyService,
                academicEventService);
    }

    @Test
    void pedidoInexistenteLanzaResourceNotFound() {
        when(itemRepository.findWithRequestById(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loader.load(ITEM_ID, Set.of(), ACTOR))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(transitionValidator, accessControl, candidateResolver);
    }

    @Test
    void pedidoResueltoSeRechazaAntesDeValidarNada() {
        stubItem(item(RoomRequestType.FINAL_EXAM).status(RoomRequestStatus.RESOLVED).build());

        assertThatThrownBy(() -> loader.load(ITEM_ID, Set.of(), ACTOR))
                .isInstanceOf(RoomRequestAlreadyNotifiedException.class);
        verifyNoInteractions(transitionValidator, accessControl, candidateResolver);
    }

    @Test
    void propagaLaTransicionInvalida() {
        stubItem(item(RoomRequestType.FINAL_EXAM).status(RoomRequestStatus.CANCELLED).build());
        doThrow(new InvalidRoomRequestTransitionException(RoomRequestStatus.CANCELLED, RoomRequestStatus.IN_EVALUATION))
                .when(transitionValidator).validateTransition(RoomRequestStatus.CANCELLED, RoomRequestStatus.IN_EVALUATION);

        assertThatThrownBy(() -> loader.load(ITEM_ID, Set.of(), ACTOR))
                .isInstanceOf(InvalidRoomRequestTransitionException.class);
        verifyNoInteractions(candidateResolver);
    }

    @Test
    void autorizaLaAccionAssignParaElActor() {
        RoomRequestItem item = item(RoomRequestType.FINAL_EXAM).build();
        stubItem(item);
        stubCandidates(item, List.of());

        loader.load(ITEM_ID, Set.of(), ACTOR);

        verify(accessControl).authorize(item, ACTOR, Action.ASSIGN);
    }

    @Test
    void sinHorarioLanzaInvalidRoomRequest() {
        stubItem(item(RoomRequestType.FINAL_EXAM).startTime(null).build());

        assertThatThrownBy(() -> loader.load(ITEM_ID, Set.of(), ACTOR))
                .isInstanceOf(InvalidRoomRequestException.class);
        verifyNoInteractions(candidateResolver);
    }

    @Test
    void sinFechaEnPedidoPuntualLanzaInvalidRoomRequest() {
        stubItem(item(RoomRequestType.FINAL_EXAM).date(null).build());

        assertThatThrownBy(() -> loader.load(ITEM_ID, Set.of(), ACTOR))
                .isInstanceOf(InvalidRoomRequestException.class);
    }

    @Test
    void pedidoPuntualUsaSuFechaYSuHorario() {
        RoomRequestItem item = item(RoomRequestType.FINAL_EXAM).build();
        stubItem(item);
        stubCandidates(item, List.of(room(10L, 30, 1L)));
        stubPermissions(Map.of());
        when(allocationOccupancyService.findOccupancy(DATE, DATE)).thenReturn(List.of());

        RoomRequestSuggestionInputLoader.Inputs inputs = loader.load(ITEM_ID, Set.of(), ACTOR);

        assertThat(inputs.itemId()).isEqualTo(ITEM_ID);
        assertThat(inputs.itemVersion()).isEqualTo(3L);
        assertThat(inputs.dates()).containsExactly(DATE);
        assertThat(inputs.startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(inputs.endTime()).isEqualTo(LocalTime.of(10, 30));
        assertThat(inputs.classroomCount()).isEqualTo(2);
        assertThat(inputs.enrolled()).isEqualTo(30);
        assertThat(inputs.subjectIds()).containsExactly(5L);
    }

    @Test
    void cambioDeAulaRegularUsaLasFechasFuturasDelDiaDeDictado() {
        RoomRequestItem item = item(RoomRequestType.REGULAR_ROOM_CHANGE).date(null).build();
        LocalDate d1 = LocalDate.of(2030, 3, 4);
        LocalDate d2 = LocalDate.of(2030, 3, 11);
        LocalDate d3 = LocalDate.of(2030, 3, 18);
        stubItem(item);
        when(occurrenceResolver.futureDatesOnDayOfWeek(item)).thenReturn(List.of(d2, d1, d3));
        stubCandidates(item, List.of());
        when(allocationOccupancyService.findOccupancy(d1, d3)).thenReturn(List.of());

        RoomRequestSuggestionInputLoader.Inputs inputs = loader.load(ITEM_ID, Set.of(), ACTOR);

        assertThat(inputs.dates()).containsExactlyInAnyOrder(d1, d2, d3);
        verify(allocationOccupancyService).findOccupancy(d1, d3);
    }

    @Test
    void excluyeLasAulasPedidasYDevuelveLasRestantesComoRoomsDelSolver() {
        RoomRequestItem item = item(RoomRequestType.FINAL_EXAM).build();
        stubItem(item);
        stubCandidates(item, List.of(room(10L, 30, 1L), room(20L, 50, 1L), room(30L, 40, 1L)));
        stubPermissions(Map.of());
        when(allocationOccupancyService.findOccupancy(DATE, DATE)).thenReturn(List.of());

        RoomRequestSuggestionInputLoader.Inputs inputs = loader.load(ITEM_ID, Set.of(20L), ACTOR);

        assertThat(inputs.classrooms()).extracting(ClassroomResponseDto::id).containsExactly(10L, 30L);
        assertThat(inputs.rooms()).extracting(OptimizerRoom::id).containsExactly(10L, 30L);
        assertThat(inputs.rooms()).extracting(OptimizerRoom::capacity).containsExactly(30, 40);
    }

    @Test
    void conEdificioDerivadoSoloQuedanLasAulasDeEseEdificio() {
        RoomRequestItem item = item(RoomRequestType.FINAL_EXAM).derivedBuildingId(2L).build();
        stubItem(item);
        stubCandidates(item, List.of(room(10L, 30, 1L), room(20L, 50, 2L)));
        stubPermissions(Map.of());
        when(allocationOccupancyService.findOccupancy(DATE, DATE)).thenReturn(List.of());

        RoomRequestSuggestionInputLoader.Inputs inputs = loader.load(ITEM_ID, Set.of(), ACTOR);

        assertThat(inputs.rooms()).extracting(OptimizerRoom::id).containsExactly(20L);
        assertThat(inputs.rooms()).extracting(OptimizerRoom::buildingId).containsExactly(2L);
    }

    @Test
    void sinAulasCandidatasNoConsultaPermisosYDevuelveListasVacias() {
        RoomRequestItem item = item(RoomRequestType.FINAL_EXAM).build();
        stubItem(item);
        stubCandidates(item, List.of(room(10L, 30, 1L)));
        when(allocationOccupancyService.findOccupancy(DATE, DATE)).thenReturn(List.of());

        RoomRequestSuggestionInputLoader.Inputs inputs = loader.load(ITEM_ID, Set.of(10L), ACTOR);

        assertThat(inputs.classrooms()).isEmpty();
        assertThat(inputs.rooms()).isEmpty();
        verify(classroomService, never()).findSubjectPermissions(anyCollection());
    }

    @Test
    void traduceLosPermisosPorMateriaYDejaAbiertaLaQueNoTienePermiso() {
        RoomRequestItem item = item(RoomRequestType.FINAL_EXAM).build();
        stubItem(item);
        stubCandidates(item, List.of(room(10L, 30, 1L), room(20L, 30, 1L)));
        stubPermissions(Map.of(10L, new ClassroomSubjectPermissionDto(10L, false, Set.of(5L, 6L))));
        when(allocationOccupancyService.findOccupancy(DATE, DATE)).thenReturn(List.of());

        RoomRequestSuggestionInputLoader.Inputs inputs = loader.load(ITEM_ID, Set.of(), ACTOR);

        OptimizerRoom restricted = inputs.rooms().get(0);
        assertThat(restricted.openToAll()).isFalse();
        assertThat(restricted.allowedSubjectIds()).containsExactlyInAnyOrder(5L, 6L);
        OptimizerRoom open = inputs.rooms().get(1);
        assertThat(open.openToAll()).isTrue();
        assertThat(open.allowedSubjectIds()).isEmpty();
    }

    @Test
    void sinMateriaEnElPedidoNoPasaSubjectIds() {
        RoomRequestItem item = item(RoomRequestType.FINAL_EXAM, null).build();
        stubItem(item);
        stubCandidates(item, List.of());
        when(allocationOccupancyService.findOccupancy(DATE, DATE)).thenReturn(List.of());

        assertThat(loader.load(ITEM_ID, Set.of(), ACTOR).subjectIds()).isEmpty();
    }

    @Test
    void ocupacionDescartaLaDelEventoRecurrenteDeOrigenYLasOcurrenciasPropias() {
        RoomRequestItem item = item(RoomRequestType.ONE_TIME_ROOM_CHANGE).sourceRecurringEventId(100L).build();
        item.assignClassrooms(List.of(10L), List.of(List.of(900L)));
        stubItem(item);
        stubCandidates(item, List.of(room(11L, 30, 1L)));
        stubPermissions(Map.of());
        LocalTime s = LocalTime.of(8, 0);
        LocalTime e = LocalTime.of(9, 0);
        when(allocationOccupancyService.findOccupancy(DATE, DATE)).thenReturn(List.of(
                new OccupiedSlot(10L, DATE, s, e, 100L, 1L, 800L),
                new OccupiedSlot(10L, DATE, s, e, 200L, 2L, 900L),
                new OccupiedSlot(10L, DATE, s, e, 300L, 3L, 901L)));

        RoomRequestSuggestionInputLoader.Inputs inputs = loader.load(ITEM_ID, Set.of(), ACTOR);

        assertThat(inputs.occupancy()).containsExactly(new OptimizerOccupancy(10L, DATE, s, e));
    }

    @Test
    void noSugiereElAulaDondeYaEstaLaClaseDelEventoDeOrigen() {
        RoomRequestItem item = item(RoomRequestType.ONE_TIME_ROOM_CHANGE).sourceRecurringEventId(100L).build();
        stubItem(item);
        stubCandidates(item, List.of(room(10L, 30, 1L), room(11L, 30, 1L)));
        stubPermissions(Map.of());
        when(allocationOccupancyService.findOccupancy(DATE, DATE)).thenReturn(List.of(
                new OccupiedSlot(10L, DATE, LocalTime.of(8, 0), LocalTime.of(9, 0), 100L, 1L, 800L)));

        RoomRequestSuggestionInputLoader.Inputs inputs = loader.load(ITEM_ID, Set.of(), ACTOR);

        assertThat(inputs.classrooms()).extracting(ClassroomResponseDto::id).containsExactly(11L);
        assertThat(inputs.rooms()).extracting(OptimizerRoom::id).containsExactly(11L);
    }

    @Test
    void conservaLaOcupacionDeOtrosEventosAunqueElPedidoNoTengaEventoDeOrigen() {
        RoomRequestItem item = item(RoomRequestType.FINAL_EXAM).build();
        stubItem(item);
        stubCandidates(item, List.of(room(10L, 30, 1L)));
        stubPermissions(Map.of());
        when(allocationOccupancyService.findOccupancy(DATE, DATE)).thenReturn(List.of(
                new OccupiedSlot(10L, DATE, LocalTime.of(9, 0), LocalTime.of(10, 0), 200L, 2L, 901L),
                new OccupiedSlot(10L, DATE, LocalTime.of(11, 0), LocalTime.of(12, 0), 201L, 3L, 902L)));

        assertThat(loader.load(ITEM_ID, Set.of(), ACTOR).occupancy()).hasSize(2);
    }

    @Test
    void cantidadInscriptaSalDelEstimadoDelPedido() {
        RoomRequestItem item = item(RoomRequestType.FINAL_EXAM).estimated(77).sourceRecurringEventId(100L).build();
        stubItem(item);
        stubCandidates(item, List.of());
        when(allocationOccupancyService.findOccupancy(DATE, DATE)).thenReturn(List.of());

        assertThat(loader.load(ITEM_ID, Set.of(), ACTOR).enrolled()).isEqualTo(77);
        verifyNoInteractions(academicEventService);
    }

    @Test
    void sinEstimadoTomaLosInscriptosDelEventoRecurrente() {
        RoomRequestItem item = item(RoomRequestType.ONE_TIME_ROOM_CHANGE).estimated(null)
                .sourceRecurringEventId(100L).build();
        stubItem(item);
        stubCandidates(item, List.of());
        AcademicEventResponseDto event = recurringEvent(48);
        when(academicEventService.findByIds(Set.of(100L))).thenReturn(List.of(event));
        when(allocationOccupancyService.findOccupancy(DATE, DATE)).thenReturn(List.of());

        assertThat(loader.load(ITEM_ID, Set.of(), ACTOR).enrolled()).isEqualTo(48);
    }

    @Test
    void sinEstimadoNiEventoDeOrigenLosInscriptosSonCero() {
        RoomRequestItem item = item(RoomRequestType.ONE_TIME_ROOM_CHANGE).estimated(null).build();
        stubItem(item);
        stubCandidates(item, List.of());
        when(allocationOccupancyService.findOccupancy(DATE, DATE)).thenReturn(List.of());

        assertThat(loader.load(ITEM_ID, Set.of(), ACTOR).enrolled()).isZero();
        verifyNoInteractions(academicEventService);
    }

    @Test
    void sinEstimadoYConEventoSinInscriptosOSinEventoEncontradoLosInscriptosSonCero() {
        RoomRequestItem item = item(RoomRequestType.ONE_TIME_ROOM_CHANGE).estimated(null)
                .sourceRecurringEventId(100L).build();
        stubItem(item);
        stubCandidates(item, List.of());
        AcademicEventResponseDto event = recurringEvent(null);
        when(academicEventService.findByIds(Set.of(100L))).thenReturn(List.of(event));
        when(allocationOccupancyService.findOccupancy(DATE, DATE)).thenReturn(List.of());

        assertThat(loader.load(ITEM_ID, Set.of(), ACTOR).enrolled()).isZero();
    }

    private void stubItem(RoomRequestItem item) {
        when(itemRepository.findWithRequestById(ITEM_ID)).thenReturn(Optional.of(item));
    }

    private void stubCandidates(RoomRequestItem item, List<ClassroomResponseDto> candidates) {
        when(candidateResolver.candidateClassrooms(item)).thenReturn(candidates);
    }

    private void stubPermissions(Map<Long, ClassroomSubjectPermissionDto> permissions) {
        when(classroomService.findSubjectPermissions(any())).thenReturn(permissions);
    }

    private static RoomRequestItem.RoomRequestItemBuilder item(RoomRequestType type) {
        return item(type, 5L);
    }

    private static RoomRequestItem.RoomRequestItemBuilder item(RoomRequestType type, Long subjectId) {
        RoomRequest request = RoomRequest.builder()
                .type(type)
                .scope(AcademicScope.GRADO)
                .teacherName("Ada Lovelace")
                .teacherEmail("ada@frc.utn.edu.ar")
                .teacherPhone("351-1234567")
                .subjectId(subjectId)
                .build();
        RoomRequestItem.RoomRequestItemBuilder builder = RoomRequestItem.builder()
                .id(ITEM_ID)
                .version(3L)
                .status(RoomRequestStatus.NEW)
                .request(request)
                .date(DATE)
                .startTime(LocalTime.of(9, 0))
                .duration(Duration.ofMinutes(90))
                .estimated(30)
                .classroomCount(2);
        return builder;
    }

    private static AcademicEventResponseDto recurringEvent(Integer enrolled) {
        return new RecurringEventResponseDto(100L, null, enrolled, LocalTime.of(9, 0), 90, DATE.getDayOfWeek(),
                DATE, DATE, null, null);
    }

    private static ClassroomResponseDto room(Long id, int capacity, Long buildingId) {
        return new ClassroomResponseDto(id, id.intValue(), capacity, buildingId, "Edificio " + buildingId, 1L, "Aula");
    }
}
