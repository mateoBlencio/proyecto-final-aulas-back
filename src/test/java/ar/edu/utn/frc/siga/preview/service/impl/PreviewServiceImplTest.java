package ar.edu.utn.frc.siga.preview.service.impl;

import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.service.AllocationConflictService;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator;
import ar.edu.utn.frc.siga.common.exception.InvalidSelectionException;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerAllocation;
import ar.edu.utn.frc.siga.preview.dto.request.ConfirmPreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewAllocationDto;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.request.ReallocationSuggestionRequestDto;
import ar.edu.utn.frc.siga.preview.dto.response.ConfirmPreviewResponseDto;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewResponseDto;
import ar.edu.utn.frc.siga.preview.dto.response.ReallocationSuggestionResponseDto;
import ar.edu.utn.frc.siga.preview.config.PreviewSettings;
import ar.edu.utn.frc.siga.preview.exception.ExpiredPreviewException;
import ar.edu.utn.frc.siga.preview.mapper.PreviewComposer;
import ar.edu.utn.frc.siga.preview.service.PreviewStore;
import ar.edu.utn.frc.siga.preview.service.ReallocationSuggestion;
import ar.edu.utn.frc.siga.preview.validator.PreviewValidator;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PreviewServiceImpl")
class PreviewServiceImplTest {

    @Mock
    private PreviewEngine previewEngine;
    @Mock
    private PreviewStore previewStore;
    @Mock
    private PreviewComposer previewComposer;
    @Mock
    private PreviewValidator previewValidator;
    @Mock
    private AllocationValidator validator;
    @Mock
    private AllocationService allocationService;
    @Mock
    private AllocationConflictService allocationConflictService;
    @Mock
    private OccurrenceService occurrenceService;
    @Mock
    private PreviewSettings previewSettings;

    private PreviewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PreviewServiceImpl(previewEngine, previewStore, previewComposer, previewValidator,
                validator, allocationService, allocationConflictService, occurrenceService, previewSettings);
        lenient().when(previewSettings.getDefaultTimeLimitSeconds()).thenReturn(30);
        lenient().when(previewSettings.getSuggestionTimeLimitSeconds()).thenReturn(2);
        lenient().when(previewEngine.loadInputs(any())).thenReturn(
                new PreviewEngine.Inputs(List.of(), Map.of(), List.of(), List.of(), List.of(), Map.of(), Map.of()));
    }

    @Test
    @DisplayName("autoPreview: eventIds y selectAll juntos → InvalidSelectionException")
    void autoPreviewEventIdsYSelectAllJuntos() {
        PreviewRequestDto request = new PreviewRequestDto(List.of(1L), true, null, null);

        assertThatThrownBy(() -> service.autoPreview(request)).isInstanceOf(InvalidSelectionException.class);
    }

    @Test
    @DisplayName("autoPreview: ni eventIds ni selectAll → InvalidSelectionException")
    void autoPreviewSinEventIdsNiSelectAll() {
        PreviewRequestDto request = new PreviewRequestDto(null, null, null, null);

        assertThatThrownBy(() -> service.autoPreview(request)).isInstanceOf(InvalidSelectionException.class);
    }

    @Test
    @DisplayName("autoPreview: selectAll=true resta excludedIds de los conflictos sin aula")
    void autoPreviewSelectAllRestaExcludedIds() {
        when(allocationConflictService.resolveAllUnallocatedEventIds()).thenReturn(List.of(1L, 2L, 3L));
        when(previewEngine.generate(any(), anyInt())).thenReturn(new OptimizationResult("prev_x", List.of()));

        service.autoPreview(new PreviewRequestDto(null, true, List.of(2L), null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Long>> idsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(previewEngine).generate(idsCaptor.capture(), anyInt());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    @DisplayName("autoPreview: usa timeLimitSeconds=30 por defecto si el request no lo trae")
    void autoPreviewUsaTimeLimitPorDefecto() {
        when(previewEngine.generate(any(), anyInt())).thenReturn(new OptimizationResult("prev_x", List.of()));

        service.autoPreview(new PreviewRequestDto(List.of(1L), null, null, null));

        ArgumentCaptor<Integer> timeLimitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(previewEngine).generate(any(), timeLimitCaptor.capture());
        assertThat(timeLimitCaptor.getValue()).isEqualTo(30);
    }

    @Test
    @DisplayName("autoPreview: la preview generada se guarda en el PreviewStore")
    void autoPreviewGuardaEnPreviewStore() {
        OptimizationResult result = new OptimizationResult("prev_x", List.of());
        when(previewEngine.generate(any(), anyInt())).thenReturn(result);

        service.autoPreview(new PreviewRequestDto(List.of(1L), null, null, null));

        verify(previewStore).save(result);
    }

    @Test
    @DisplayName("getPreview: id inexistente lanza ExpiredPreviewException")
    void getPreviewInexistenteLanzaExpirado() {
        when(previewStore.get("prev_missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPreview("prev_missing")).isInstanceOf(ExpiredPreviewException.class);
    }

    @Test
    @DisplayName("getPreview: recompone el DTO desde la preview guardada")
    void getPreviewRecompone() {
        OptimizationResult result = new OptimizationResult("prev_x", List.of(new OptimizerAllocation("1", 5L)));
        when(previewStore.get("prev_x")).thenReturn(Optional.of(result));
        PreviewResponseDto expected = new PreviewResponseDto("prev_x", List.of(), List.of());
        when(previewComposer.compose(eq(result), any(), any(), any(), any(), any(), any())).thenReturn(expected);

        PreviewResponseDto actual = service.getPreview("prev_x");

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("confirm: preview inexistente lanza ExpiredPreviewException")
    void confirmPreviewInexistenteLanzaExpirado() {
        when(previewStore.get("prev_missing")).thenReturn(Optional.empty());
        ConfirmPreviewRequestDto request = new ConfirmPreviewRequestDto(List.of(new PreviewAllocationDto(1L, 5L)));

        assertThatThrownBy(() -> service.confirm("prev_missing", request))
                .isInstanceOf(ExpiredPreviewException.class);
    }

    @Test
    @DisplayName("confirm: todas las aulas en null → responde skipped sin tocar allocationService")
    void confirmTodasLasAulasNullNoAplicaNada() {
        OptimizationResult result = new OptimizationResult("prev_x", List.of(new OptimizerAllocation("1", null)));
        when(previewStore.get("prev_x")).thenReturn(Optional.of(result));
        ConfirmPreviewRequestDto request = new ConfirmPreviewRequestDto(List.of(new PreviewAllocationDto(1L, null)));

        ConfirmPreviewResponseDto response = service.confirm("prev_x", request);

        assertThat(response.applied()).isEmpty();
        assertThat(response.skippedEventIds()).containsExactly(1L);
        verifyNoInteractions(allocationService);
        verify(previewStore, never()).remove(any());
    }

    @Test
    @DisplayName("confirm: aplica la propuesta y borra el preview del store")
    void confirmAplicaYBorraPreview() {
        OptimizationResult result = new OptimizationResult("prev_x", List.of(new OptimizerAllocation("1", 5L)));
        when(previewStore.get("prev_x")).thenReturn(Optional.of(result));
        when(occurrenceService.findSlotsByEvents(any(), any())).thenReturn(List.of());
        when(allocationService.reallocate(any())).thenReturn(List.of());
        ConfirmPreviewRequestDto request = new ConfirmPreviewRequestDto(List.of(new PreviewAllocationDto(1L, 5L)));

        service.confirm("prev_x", request);

        verify(previewStore, times(1)).remove("prev_x");
        // Confirm sigue en la sobrecarga estricta (margen 0): no puede heredar la tolerancia
        // de la asignación manual y filtrarla al optimizador.
        verify(validator).validateNoOverlap(anyList(), anyList());
    }

    @Test
    @DisplayName("suggestReallocation: ocurrencias de dos eventos distintos → InvalidSelectionException")
    void suggestReallocationOcurrenciasDeDosEventosLanzaInvalidSelectionException() {
        when(allocationService.resolveOccurrences(any())).thenReturn(List.of(
                slot(10L, 1L, LocalDate.of(2026, 3, 2)),
                slot(20L, 2L, LocalDate.of(2026, 3, 2))));
        ReallocationSuggestionRequestDto request =
                new ReallocationSuggestionRequestDto(List.of(10L, 20L), null, null, null, null);

        assertThatThrownBy(() -> service.suggestReallocation(request))
                .isInstanceOf(InvalidSelectionException.class);
    }

    @Test
    @DisplayName("suggestReallocation: resolución vacía → AllocationConflictException")
    void suggestReallocationResolucionVaciaLanzaAllocationConflictException() {
        when(allocationService.resolveOccurrences(any())).thenReturn(List.of());
        ReallocationSuggestionRequestDto request =
                new ReallocationSuggestionRequestDto(List.of(10L), null, null, null, null);

        assertThatThrownBy(() -> service.suggestReallocation(request))
                .isInstanceOf(AllocationConflictException.class);
    }

    @Test
    @DisplayName("suggestReallocation: occurrenceIds se mapea a AllocationTarget.Occurrences")
    void suggestReallocationOccurrenceIdsMapeaATargetOccurrences() {
        when(allocationService.resolveOccurrences(any())).thenReturn(
                List.of(slot(10L, 1L, LocalDate.of(2026, 3, 2))));
        stubSuggestSinAula();
        ReallocationSuggestionRequestDto request =
                new ReallocationSuggestionRequestDto(List.of(10L, 20L), null, null, null, null);

        service.suggestReallocation(request);

        ArgumentCaptor<AllocationTarget> targetCaptor = ArgumentCaptor.forClass(AllocationTarget.class);
        verify(allocationService).resolveOccurrences(targetCaptor.capture());
        assertThat(targetCaptor.getValue()).isEqualTo(new AllocationTarget.Occurrences(List.of(10L, 20L)));
    }

    @Test
    @DisplayName("suggestReallocation: eventId+from+to se mapea a AllocationTarget.EventRange con esas fechas")
    void suggestReallocationEventIdFromToMapeaATargetEventRange() {
        LocalDate from = LocalDate.of(2026, 3, 2);
        LocalDate to = LocalDate.of(2026, 3, 16);
        when(allocationService.resolveOccurrences(any())).thenReturn(
                List.of(slot(10L, 42L, LocalDate.of(2026, 3, 2))));
        stubSuggestSinAula();
        ReallocationSuggestionRequestDto request =
                new ReallocationSuggestionRequestDto(null, 42L, from, to, null);

        service.suggestReallocation(request);

        ArgumentCaptor<AllocationTarget> targetCaptor = ArgumentCaptor.forClass(AllocationTarget.class);
        verify(allocationService).resolveOccurrences(targetCaptor.capture());
        assertThat(targetCaptor.getValue()).isEqualTo(new AllocationTarget.EventRange(42L, from, to));
    }

    @Test
    @DisplayName("suggestReallocation: con aula, saveSuggestion recibe las occurrenceId de los slots y el classroomId")
    void suggestReallocationConAulaGuardaSugerencia() {
        when(allocationService.resolveOccurrences(any())).thenReturn(List.of(
                slot(10L, 1L, LocalDate.of(2026, 3, 2)),
                slot(20L, 1L, LocalDate.of(2026, 3, 9))));
        ClassroomResponseDto classroom = new ClassroomResponseDto(5L, 5, 40, 1L, "Edificio 1", 1L, "Tipo");
        when(previewEngine.suggest(any(), any(), any(), anyInt()))
                .thenReturn(new PreviewEngine.Suggestion(null, classroom, 0));
        ReallocationSuggestionRequestDto request =
                new ReallocationSuggestionRequestDto(List.of(10L, 20L), null, null, null, null);

        ReallocationSuggestionResponseDto response = service.suggestReallocation(request);

        ArgumentCaptor<ReallocationSuggestion> suggestionCaptor = ArgumentCaptor.forClass(ReallocationSuggestion.class);
        verify(previewStore).saveSuggestion(suggestionCaptor.capture());
        assertThat(suggestionCaptor.getValue().occurrenceIds()).containsExactly(10L, 20L);
        assertThat(suggestionCaptor.getValue().classroomId()).isEqualTo(5L);
        assertThat(response.suggestionId()).isNotBlank();
        assertThat(response.status()).isEqualTo(ReallocationSuggestionResponseDto.SuggestionStatus.SUGGESTED);
    }

    @Test
    @DisplayName("suggestReallocation: sin aula, no guarda sugerencia y suggestionId queda null")
    void suggestReallocationSinAulaNoGuardaSugerencia() {
        when(allocationService.resolveOccurrences(any())).thenReturn(
                List.of(slot(10L, 1L, LocalDate.of(2026, 3, 2))));
        stubSuggestSinAula();
        ReallocationSuggestionRequestDto request =
                new ReallocationSuggestionRequestDto(List.of(10L), null, null, null, null);

        ReallocationSuggestionResponseDto response = service.suggestReallocation(request);

        verify(previewStore, never()).saveSuggestion(any());
        assertThat(response.suggestionId()).isNull();
        assertThat(response.status()).isEqualTo(ReallocationSuggestionResponseDto.SuggestionStatus.NO_ROOM_AVAILABLE);
    }

    @Test
    @DisplayName("confirmReallocationSuggestion: reallocate recibe comando AUTOMATIC con las ocurrencias y el aula guardadas")
    void confirmReallocationSuggestionAplicaConSourceAutomatic() {
        when(previewStore.takeSuggestion("sug_x"))
                .thenReturn(Optional.of(new ReallocationSuggestion("sug_x", 1L, List.of(10L, 20L), 5L)));
        when(allocationService.reallocate(any())).thenReturn(List.of());

        service.confirmReallocationSuggestion("sug_x");

        ArgumentCaptor<AllocationCommand> commandCaptor = ArgumentCaptor.forClass(AllocationCommand.class);
        verify(allocationService).reallocate(commandCaptor.capture());
        AllocationCommand command = commandCaptor.getValue();
        assertThat(command.source()).isEqualTo(AllocationSource.AUTOMATIC);
        assertThat(command.items()).containsExactly(
                new AllocationItem(new AllocationTarget.Occurrences(List.of(10L, 20L)), 5L));
    }

    @Test
    @DisplayName("confirmReallocationSuggestion: sugerencia inexistente → ExpiredPreviewException, reallocate nunca se llama")
    void confirmReallocationSuggestionSinSugerenciaLanzaExpirado() {
        when(previewStore.takeSuggestion("sug_missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmReallocationSuggestion("sug_missing"))
                .isInstanceOf(ExpiredPreviewException.class);

        verify(allocationService, never()).reallocate(any());
    }

    private void stubSuggestSinAula() {
        lenient().when(previewEngine.suggest(any(), any(), any(), anyInt()))
                .thenReturn(new PreviewEngine.Suggestion(null, null, 0));
    }

    private OccurrenceSlotDto slot(Long occurrenceId, Long eventId, LocalDate date) {
        return new OccurrenceSlotDto(occurrenceId, eventId, date, LocalTime.of(8, 0), LocalTime.of(9, 30),
                OccurrenceStatus.NEEDS_ROOM, 30);
    }
}
