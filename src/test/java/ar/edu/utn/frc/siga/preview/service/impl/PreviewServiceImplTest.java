package ar.edu.utn.frc.siga.preview.service.impl;

import ar.edu.utn.frc.siga.allocation.service.AllocationConflictService;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator;
import ar.edu.utn.frc.siga.common.exception.InvalidSelectionException;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerAllocation;
import ar.edu.utn.frc.siga.preview.dto.request.ConfirmPreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewAllocationDto;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.response.ConfirmPreviewResponseDto;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewResponseDto;
import ar.edu.utn.frc.siga.preview.exception.ExpiredPreviewException;
import ar.edu.utn.frc.siga.preview.mapper.PreviewComposer;
import ar.edu.utn.frc.siga.preview.service.PreviewStore;
import ar.edu.utn.frc.siga.preview.validator.PreviewValidator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

    private PreviewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PreviewServiceImpl(previewEngine, previewStore, previewComposer, previewValidator,
                validator, allocationService, allocationConflictService, occurrenceService);
        lenient().when(previewEngine.loadInputs(any())).thenReturn(
                new PreviewEngine.Inputs(List.of(), Map.of(), List.of(), List.of(), List.of(), Map.of()));
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
        when(allocationConflictService.resolveAllUnassignedEventIds()).thenReturn(List.of(1L, 2L, 3L));
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
        OptimizationResult result = new OptimizationResult("prev_x", List.of(new OptimizerAllocation("1", 5)));
        when(previewStore.get("prev_x")).thenReturn(Optional.of(result));
        PreviewResponseDto expected = new PreviewResponseDto("prev_x", List.of(), List.of());
        when(previewComposer.compose(eq(result), any(), any(), any(), any(), any())).thenReturn(expected);

        PreviewResponseDto actual = service.getPreview("prev_x");

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("confirm: preview inexistente lanza ExpiredPreviewException")
    void confirmPreviewInexistenteLanzaExpirado() {
        when(previewStore.get("prev_missing")).thenReturn(Optional.empty());
        ConfirmPreviewRequestDto request = new ConfirmPreviewRequestDto(List.of(new PreviewAllocationDto(1L, 5)));

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
        OptimizationResult result = new OptimizationResult("prev_x", List.of(new OptimizerAllocation("1", 5)));
        when(previewStore.get("prev_x")).thenReturn(Optional.of(result));
        when(occurrenceService.findSlotsByEvents(any(), any())).thenReturn(List.of());
        when(allocationService.reallocate(any())).thenReturn(List.of());
        ConfirmPreviewRequestDto request = new ConfirmPreviewRequestDto(List.of(new PreviewAllocationDto(1L, 5)));

        service.confirm("prev_x", request);

        verify(previewStore, times(1)).remove("prev_x");
    }
}
