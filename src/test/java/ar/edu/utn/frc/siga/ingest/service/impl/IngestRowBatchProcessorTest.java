package ar.edu.utn.frc.siga.ingest.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.ingest.dto.ImportedRow;
import ar.edu.utn.frc.siga.ingest.dto.RowDto;
import ar.edu.utn.frc.siga.ingest.exception.InvalidRowException;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IngestRowBatchProcessor")
class IngestRowBatchProcessorTest {

    @Mock
    private IngestRowResolver rowResolver;
    @Mock
    private AcademicEventService academicEventService;

    private IngestRowBatchProcessor processor() {
        return new IngestRowBatchProcessor(rowResolver, academicEventService);
    }

    private RowDto row(String termType) {
        return new RowDto("6301", 1, "105", "Edificio Central", DayOfWeek.MONDAY, termType,
                LocalTime.of(18, 0), LocalTime.of(19, 30), null, 1, 1, 100, "Materia", 30);
    }

    private IngestRowResolver.ResolvedRefs resolved(Long classroomBuildingId) {
        SubjectResponseDto subject = new SubjectResponseDto(10L, 100, "Materia", "Anual", null);
        CommissionResponseDto commission = new CommissionResponseDto(20L, "6301", null);
        BuildingResponseDto building = new BuildingResponseDto(5L, "Edificio Central", true);
        ClassroomResponseDto classroom = new ClassroomResponseDto(
                50L, 105, 40, classroomBuildingId, "Edificio Real", 1L, "Aula");
        CreateRecurringEventRequestDto request = new CreateRecurringEventRequestDto(
                30, LocalTime.of(18, 0), 90, DayOfWeek.MONDAY, null, null, 10L, 20L);
        return new IngestRowResolver.ResolvedRefs(subject, commission, building, classroom, request);
    }

    @Test
    @DisplayName("lote resuelto: un solo llamado al bulk find-or-create, asignación pendiente por evento")
    void loteResueltoLlamaAlBulkUnaVez() {
        when(rowResolver.resolveRefs(any(), any(), anyInt(), any(), any(), any(), any()))
                .thenReturn(resolved(5L));
        when(academicEventService.findOrCreateRecurringEvents(any()))
                .thenReturn(List.of(new FindOrCreateResult<>(1L, true)));

        IngestRowBatchProcessor.BatchResult result = processor().process(List.of(new ImportedRow(7, row("Anual"))), 2026);

        assertThat(result.processedRows()).isEqualTo(1);
        assertThat(result.eventsCreated()).isEqualTo(1);
        assertThat(result.skippedRows()).isEmpty();
        assertThat(result.rowWarnings()).isEmpty();
        assertThat(result.pendingAllocations()).hasSize(1);
        assertThat(result.pendingAllocations().getFirst().target()).isEqualTo(new AllocationTarget.Event(1L));
        assertThat(result.pendingAllocations().getFirst().classroomId()).isEqualTo(50L);
        verify(academicEventService, times(1)).findOrCreateRecurringEvents(any());
    }

    @Test
    @DisplayName("aula resuelta en un edificio distinto al informado: agrega warning pero sigue procesando")
    void aulaEnEdificioDistintoAgregaWarning() {
        when(rowResolver.resolveRefs(any(), any(), anyInt(), any(), any(), any(), any()))
                .thenReturn(resolved(7L)); // classroom.buildingId=7 != building.id=5
        when(academicEventService.findOrCreateRecurringEvents(any()))
                .thenReturn(List.of(new FindOrCreateResult<>(1L, false)));

        IngestRowBatchProcessor.BatchResult result = processor().process(List.of(new ImportedRow(7, row("Anual"))), 2026);

        assertThat(result.processedRows()).isEqualTo(1);
        assertThat(result.rowWarnings()).hasSize(1);
        assertThat(result.rowWarnings().getFirst().message()).contains("Edificio Real");
    }

    @Test
    @DisplayName("fila que no resuelve contra el catálogo: se saltea, no entra al bulk, y el lote sigue")
    void filaQueNoResuelveNoEntraAlBulk() {
        when(rowResolver.resolveRefs(any(), any(), anyInt(), any(), any(), any(), any()))
                .thenThrow(ResourceNotFoundException.of("Subject", 999))
                .thenReturn(resolved(5L));
        when(academicEventService.findOrCreateRecurringEvents(any()))
                .thenReturn(List.of(new FindOrCreateResult<>(2L, true)));

        IngestRowBatchProcessor.BatchResult result = processor().process(
                List.of(new ImportedRow(7, row("Anual")), new ImportedRow(8, row("Anual"))), 2026);

        assertThat(result.processedRows()).isEqualTo(1);
        assertThat(result.skippedRows()).hasSize(1);
        assertThat(result.skippedRows().getFirst().row()).isEqualTo(7);
        assertThat(result.pendingAllocations()).hasSize(1);

        ArgumentCaptor<List<CreateRecurringEventRequestDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(academicEventService).findOrCreateRecurringEvents(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    @DisplayName("lote de varias filas: eventsCreated cuenta sólo los created y cada fila arma su asignación pendiente")
    void loteConVariasFilasMezclaCreatedYFound() {
        when(rowResolver.resolveRefs(any(), any(), anyInt(), any(), any(), any(), any()))
                .thenReturn(resolved(5L));
        when(academicEventService.findOrCreateRecurringEvents(any())).thenReturn(List.of(
                new FindOrCreateResult<>(1L, true),
                new FindOrCreateResult<>(2L, false),
                new FindOrCreateResult<>(3L, true)));

        IngestRowBatchProcessor.BatchResult result = processor().process(List.of(
                new ImportedRow(7, row("Anual")),
                new ImportedRow(8, row("Anual")),
                new ImportedRow(9, row("Anual"))), 2026);

        assertThat(result.processedRows()).isEqualTo(3);
        assertThat(result.eventsCreated()).isEqualTo(2);
        assertThat(result.skippedRows()).isEmpty();
        assertThat(result.pendingAllocations()).extracting(pa -> pa.target())
                .containsExactly(new AllocationTarget.Event(1L), new AllocationTarget.Event(2L),
                        new AllocationTarget.Event(3L));
        verify(academicEventService, times(1)).findOrCreateRecurringEvents(any());
    }

    @Test
    @DisplayName("término (dictado) desconocido: InvalidRowException corta antes de resolver, no es un skip")
    void terminoDesconocidoLanzaYCortaElLote() {
        assertThatThrownBy(() -> processor().process(List.of(new ImportedRow(7, row("Trimestre Fantasma"))), 2026))
                .isInstanceOf(InvalidRowException.class)
                .hasMessageContaining("Trimestre Fantasma");
    }

    @Test
    @DisplayName("lote vacío tras saltear todas las filas: no llama al bulk")
    void loteVacioNoLlamaAlBulk() {
        when(rowResolver.resolveRefs(any(), any(), anyInt(), any(), any(), any(), any()))
                .thenThrow(ResourceNotFoundException.of("Subject", 999));

        IngestRowBatchProcessor.BatchResult result = processor().process(List.of(new ImportedRow(7, row("Anual"))), 2026);

        assertThat(result.processedRows()).isZero();
        assertThat(result.skippedRows()).hasSize(1);
        org.mockito.Mockito.verifyNoInteractions(academicEventService);
    }

    @Test
    @DisplayName("startDate/endDate del año se derivan del TermType de la fila")
    void startEndDateSeDerivanDelTermType() {
        when(rowResolver.resolveRefs(any(), any(), anyInt(), any(), any(), any(), any()))
                .thenReturn(resolved(5L));
        when(academicEventService.findOrCreateRecurringEvents(any()))
                .thenReturn(List.of(new FindOrCreateResult<>(1L, true)));

        processor().process(List.of(new ImportedRow(7, row("1 Cuat."))), 2026);

        ArgumentCaptor<TermType> termTypeCaptor = ArgumentCaptor.forClass(TermType.class);
        verify(rowResolver).resolveRefs(any(), termTypeCaptor.capture(), anyInt(), any(), any(), any(), any());
        assertThat(termTypeCaptor.getValue()).isEqualTo(TermType.PRIMER_CUATRIMESTRE);
    }
}
