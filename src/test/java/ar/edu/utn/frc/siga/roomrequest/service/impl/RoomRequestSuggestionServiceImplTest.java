package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerAllocation;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerEvent;
import ar.edu.utn.frc.siga.optimizer.service.OptimizerService;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestSuggestionResponseDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestSuggestionResponseDto.SuggestionStatus;
import ar.edu.utn.frc.siga.roomrequest.exception.ExpiredSuggestionException;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestResolutionService;
import ar.edu.utn.frc.siga.settings.api.SettingsReader;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomRequestSuggestionServiceImplTest {

    private static final Long ITEM_ID = 7L;
    private static final String ACTOR = "sub@utn.edu.ar";

    @Mock RoomRequestSuggestionInputLoader inputLoader;
    @Mock OptimizerService optimizerService;
    @Mock SettingsReader settingsReader;
    @Mock RoomRequestItemRepository itemRepository;
    @Mock RoomRequestResolutionService resolutionService;

    private RoomRequestSuggestionStore store;
    private RoomRequestSuggestionServiceImpl service;

    @BeforeEach
    void setUp() {
        when(settingsReader.getLong(SettingKey.PREVIEW_TTL_MINUTES)).thenReturn(30L);
        store = new RoomRequestSuggestionStore(settingsReader);
        store.init();
        service = new RoomRequestSuggestionServiceImpl(inputLoader, store, optimizerService, settingsReader,
                itemRepository, resolutionService);
    }

    @Test
    void sinAulasCandidatasNoCorreElSolver() {
        when(inputLoader.load(eq(ITEM_ID), any(), eq(ACTOR))).thenReturn(inputs(1, List.of()));

        RoomRequestSuggestionResponseDto response = service.suggest(ITEM_ID, null, ACTOR);

        assertThat(response.status()).isEqualTo(SuggestionStatus.NO_ROOM_AVAILABLE);
        assertThat(response.suggestionId()).isNull();
        verify(optimizerService, never()).optimize(any(), any(), any(), anyInt());
    }

    @Test
    void armaUnEventoPorAulaPedidaYDevuelveSuggested() {
        stubSolver(new OptimizerAllocation("rr-7-1", 20L), new OptimizerAllocation("rr-7-0", 10L));
        when(inputLoader.load(eq(ITEM_ID), any(), eq(ACTOR))).thenReturn(inputs(2, List.of(room(10L, 30), room(20L, 20))));

        RoomRequestSuggestionResponseDto response = service.suggest(ITEM_ID, null, ACTOR);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OptimizerEvent>> events = ArgumentCaptor.forClass(List.class);
        verify(optimizerService).optimize(events.capture(), any(), any(), eq(2));
        assertThat(events.getValue()).extracting(OptimizerEvent::planningId).containsExactly("rr-7-0", "rr-7-1");
        assertThat(response.status()).isEqualTo(SuggestionStatus.SUGGESTED);
        assertThat(response.classrooms()).extracting(ClassroomResponseDto::id).containsExactly(10L, 20L);
        assertThat(response.overcrowdedBy()).isEqualTo(5);
        assertThat(response.suggestionId()).startsWith("sug_");
    }

    @Test
    void devuelvePartialSiElSolverCubreMenosAulasQueLasPedidas() {
        stubSolver(new OptimizerAllocation("rr-7-0", 10L), new OptimizerAllocation("rr-7-1", null));
        when(inputLoader.load(eq(ITEM_ID), any(), eq(ACTOR))).thenReturn(inputs(2, List.of(room(10L, 30))));

        RoomRequestSuggestionResponseDto response = service.suggest(ITEM_ID, null, ACTOR);

        assertThat(response.status()).isEqualTo(SuggestionStatus.PARTIAL);
        assertThat(response.classrooms()).extracting(ClassroomResponseDto::id).containsExactly(10L);
    }

    @Test
    void devuelveNoRoomAvailableSiElSolverNoAsignaNinguna() {
        stubSolver(new OptimizerAllocation("rr-7-0", null));
        when(inputLoader.load(eq(ITEM_ID), any(), eq(ACTOR))).thenReturn(inputs(1, List.of(room(10L, 30))));

        RoomRequestSuggestionResponseDto response = service.suggest(ITEM_ID, null, ACTOR);

        assertThat(response.status()).isEqualTo(SuggestionStatus.NO_ROOM_AVAILABLE);
        assertThat(response.suggestionId()).isNull();
    }

    @Test
    void pasaLasAulasExcluidasAlLoader() {
        when(inputLoader.load(eq(ITEM_ID), eq(Set.of(3L, 4L)), eq(ACTOR))).thenReturn(inputs(1, List.of()));

        service.suggest(ITEM_ID, List.of(3L, 4L), ACTOR);

        verify(inputLoader).load(ITEM_ID, Set.of(3L, 4L), ACTOR);
    }

    @Test
    void confirmAsignaLasAulasSugeridasEnModoAutomatico() {
        String suggestionId = suggestOneRoom();
        stubItemVersion(1L);
        RoomRequestItemResponseDto expected = mock(RoomRequestItemResponseDto.class);
        when(resolutionService.assignAutomatic(ITEM_ID, List.of(10L), "motivo", ACTOR)).thenReturn(expected);

        assertThat(service.confirm(ITEM_ID, suggestionId, "motivo", ACTOR)).isSameAs(expected);
    }

    @Test
    void confirmConsumeLaSugerenciaAlPrimerIntento() {
        String suggestionId = suggestOneRoom();
        stubItemVersion(1L);

        service.confirm(ITEM_ID, suggestionId, null, ACTOR);

        assertThatThrownBy(() -> service.confirm(ITEM_ID, suggestionId, null, ACTOR))
                .isInstanceOf(ExpiredSuggestionException.class);
    }

    @Test
    void confirmFallaSiLaSugerenciaNoExiste() {
        assertThatThrownBy(() -> service.confirm(ITEM_ID, "sug_inexistente", null, ACTOR))
                .isInstanceOf(ExpiredSuggestionException.class);
        verify(resolutionService, never()).assignAutomatic(any(), any(), any(), any());
    }

    @Test
    void confirmFallaSiLaSugerenciaEsDeOtroPedido() {
        String suggestionId = suggestOneRoom();

        assertThatThrownBy(() -> service.confirm(99L, suggestionId, null, ACTOR))
                .isInstanceOf(ExpiredSuggestionException.class);
    }

    @Test
    void confirmFallaSiElPedidoCambioDesdeLaSugerencia() {
        String suggestionId = suggestOneRoom();
        stubItemVersion(2L);

        assertThatThrownBy(() -> service.confirm(ITEM_ID, suggestionId, null, ACTOR))
                .isInstanceOf(ExpiredSuggestionException.class);
        verify(resolutionService, never()).assignAutomatic(any(), any(), any(), any());
    }

    private String suggestOneRoom() {
        stubSolver(new OptimizerAllocation("rr-7-0", 10L));
        when(inputLoader.load(eq(ITEM_ID), any(), eq(ACTOR))).thenReturn(inputs(1, List.of(room(10L, 30))));
        return service.suggest(ITEM_ID, null, ACTOR).suggestionId();
    }

    private void stubSolver(OptimizerAllocation... allocations) {
        when(settingsReader.getInt(SettingKey.PREVIEW_SUGGESTION_TIME_LIMIT_SECONDS)).thenReturn(2);
        when(optimizerService.optimize(any(), any(), any(), anyInt()))
                .thenReturn(new OptimizationResult(null, List.of(allocations)));
    }

    private void stubItemVersion(Long version) {
        RoomRequestItem item = mock(RoomRequestItem.class);
        when(item.getVersion()).thenReturn(version);
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
    }

    private static ClassroomResponseDto room(Long id, int capacity) {
        return new ClassroomResponseDto(id, id.intValue(), capacity, 1L, "Edificio", 1L, "Aula");
    }

    private static RoomRequestSuggestionInputLoader.Inputs inputs(int classroomCount, List<ClassroomResponseDto> rooms) {
        return new RoomRequestSuggestionInputLoader.Inputs(ITEM_ID, 1L, classroomCount, 25, LocalTime.of(10, 0),
                LocalTime.of(12, 0), Set.of(LocalDate.of(2030, 1, 1)), Set.of(), rooms,
                rooms.stream().map(c -> new ar.edu.utn.frc.siga.optimizer.model.OptimizerRoom(
                        c.id(), c.capacity(), c.buildingId())).toList(),
                List.of());
    }
}
