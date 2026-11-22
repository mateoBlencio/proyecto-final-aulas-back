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
import ar.edu.utn.frc.siga.ingest.dto.RowIssueDto;
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
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    private IngestRowBatchProcessor.BatchResult process(List<ImportedRow> rows, int year) {
        IngestRowBatchProcessor processor = processor();
        return processor.write(processor.resolve(rows, year));
    }

    private IngestRowResolver.ResolvedRefs resolved(Long classroomBuildingId) {
        return resolved(classroomBuildingId, 50L);
    }

    private IngestRowResolver.ResolvedRefs resolvedInClassroom(long classroomId) {
        return resolved(5L, classroomId);
    }

    private IngestRowResolver.ResolvedRefs resolved(Long classroomBuildingId, long classroomId) {
        SubjectResponseDto subject = new SubjectResponseDto(10L, 100, "Materia", "Anual", null, true, null);
        CommissionResponseDto commission = new CommissionResponseDto(20L, "6301", null, null);
        BuildingResponseDto building = new BuildingResponseDto(5L, "Edificio Central", true, null);
        ClassroomResponseDto classroom = new ClassroomResponseDto(
                classroomId, 105, 40, classroomBuildingId, "Edificio Real", 1L, "Aula", null, true);
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

        IngestRowBatchProcessor.BatchResult result = process(List.of(new ImportedRow(7, row("Anual"))), 2026);

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

        IngestRowBatchProcessor.BatchResult result = process(List.of(new ImportedRow(7, row("Anual"))), 2026);

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

        IngestRowBatchProcessor.BatchResult result = process(
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

        IngestRowBatchProcessor.BatchResult result = process(List.of(
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
        assertThatThrownBy(() -> process(List.of(new ImportedRow(7, row("Trimestre Fantasma"))), 2026))
                .isInstanceOf(InvalidRowException.class)
                .hasMessageContaining("Trimestre Fantasma");
    }

    @Test
    @DisplayName("término desconocido en la fila 9: resolve lanza antes de resolver cualquier fila anterior")
    void terminoDesconocidoEnFilaTardiaCortaAntesDeResolverLaPrimera() {
        List<ImportedRow> rows = List.of(
                new ImportedRow(7, row("Anual")),
                new ImportedRow(8, row("Anual")),
                new ImportedRow(9, row("Trimestre Fantasma")));

        assertThatThrownBy(() -> processor().resolve(rows, 2026))
                .isInstanceOf(InvalidRowException.class)
                .hasMessageContaining("Trimestre Fantasma")
                .hasMessageContaining("row 9");

        verifyNoInteractions(rowResolver, academicEventService);
    }

    @Test
    @DisplayName("resolve saltea la fila que no resuelve, conserva el resto y no escribe eventos")
    void resolveSalteaFilaYNoEscribeEventos() {
        when(rowResolver.resolveRefs(any(), any(), anyInt(), any(), any(), any(), any()))
                .thenThrow(ResourceNotFoundException.of("Subject", 999))
                .thenReturn(resolved(5L));

        IngestRowBatchProcessor.ResolvedBatch batch = processor().resolve(
                List.of(new ImportedRow(7, row("Anual")), new ImportedRow(8, row("Anual"))), 2026);

        assertThat(batch.skippedRows()).extracting(RowIssueDto::row).containsExactly(7);
        assertThat(batch.work()).extracting(IngestRowBatchProcessor.RowWork::rowNum).containsExactly(8);
        verifyNoInteractions(academicEventService);
    }

    @Test
    @DisplayName("ING-01: aulas 5 y 7 para el mismo evento: una advertencia por fila, ningún item para ese evento, y otro evento sí se asigna")
    void mismoEventoConAulasDistintasNoAsignaYAdvierteCadaFila() {
        when(rowResolver.resolveRefs(any(), any(), anyInt(), any(), any(), any(), any()))
                .thenReturn(resolvedInClassroom(5L), resolvedInClassroom(7L), resolvedInClassroom(9L));
        when(academicEventService.findOrCreateRecurringEvents(any())).thenReturn(List.of(
                new FindOrCreateResult<>(1L, true),
                new FindOrCreateResult<>(1L, false),
                new FindOrCreateResult<>(2L, true)));

        IngestRowBatchProcessor.BatchResult result = process(List.of(
                new ImportedRow(7, row("Anual")),
                new ImportedRow(8, row("Anual")),
                new ImportedRow(9, row("Anual"))), 2026);

        assertThat(result.rowWarnings()).extracting(RowIssueDto::row, RowIssueDto::message).containsExactly(
                tuple(7, "La fila 7 apunta al mismo evento que las filas [8] con otra aula; no se asignó ninguna"),
                tuple(8, "La fila 8 apunta al mismo evento que las filas [7] con otra aula; no se asignó ninguna"));
        assertThat(result.pendingAllocations()).hasSize(1);
        assertThat(result.pendingAllocations().getFirst().target()).isEqualTo(new AllocationTarget.Event(2L));
        assertThat(result.pendingAllocations().getFirst().classroomId()).isEqualTo(9L);
        assertThat(result.processedRows()).isEqualTo(3);
    }

    @Test
    @DisplayName("ING-01: dos filas iguales con la misma aula: un solo item y ninguna advertencia")
    void filaRepetidaConLaMismaAulaGeneraUnItemSinAdvertir() {
        when(rowResolver.resolveRefs(any(), any(), anyInt(), any(), any(), any(), any()))
                .thenReturn(resolvedInClassroom(5L), resolvedInClassroom(5L));
        when(academicEventService.findOrCreateRecurringEvents(any())).thenReturn(List.of(
                new FindOrCreateResult<>(1L, true),
                new FindOrCreateResult<>(1L, false)));

        IngestRowBatchProcessor.BatchResult result = process(List.of(
                new ImportedRow(7, row("Anual")),
                new ImportedRow(8, row("Anual"))), 2026);

        assertThat(result.rowWarnings()).isEmpty();
        assertThat(result.pendingAllocations()).hasSize(1);
        assertThat(result.pendingAllocations().getFirst().target()).isEqualTo(new AllocationTarget.Event(1L));
        assertThat(result.pendingAllocations().getFirst().classroomId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("ING-01: A=5, B=5, C=7 para el mismo evento: la advertencia de A y B menciona [C], la de C menciona [A, B]")
    void advertenciaListaLasFilasConOtraAula() {
        when(rowResolver.resolveRefs(any(), any(), anyInt(), any(), any(), any(), any()))
                .thenReturn(resolvedInClassroom(5L), resolvedInClassroom(5L), resolvedInClassroom(7L));
        when(academicEventService.findOrCreateRecurringEvents(any())).thenReturn(List.of(
                new FindOrCreateResult<>(1L, true),
                new FindOrCreateResult<>(1L, false),
                new FindOrCreateResult<>(1L, false)));

        IngestRowBatchProcessor.BatchResult result = process(List.of(
                new ImportedRow(7, row("Anual")),
                new ImportedRow(8, row("Anual")),
                new ImportedRow(9, row("Anual"))), 2026);

        assertThat(result.rowWarnings()).extracting(RowIssueDto::row, RowIssueDto::message).containsExactly(
                tuple(7, "La fila 7 apunta al mismo evento que las filas [9] con otra aula; no se asignó ninguna"),
                tuple(8, "La fila 8 apunta al mismo evento que las filas [9] con otra aula; no se asignó ninguna"),
                tuple(9, "La fila 9 apunta al mismo evento que las filas [7, 8] con otra aula; no se asignó ninguna"));
        assertThat(result.pendingAllocations()).isEmpty();
    }

    @Test
    @DisplayName("advertencia de edificio y de ING-01 en la misma fila: rowWarnings sale ordenado por fila")
    void advertenciasDeEdificioYConflictoSalenOrdenadasPorFila() {
        when(rowResolver.resolveRefs(any(), any(), anyInt(), any(), any(), any(), any()))
                .thenReturn(resolvedInClassroom(5L), resolved(7L, 6L), resolved(7L, 8L));
        when(academicEventService.findOrCreateRecurringEvents(any())).thenReturn(List.of(
                new FindOrCreateResult<>(1L, true),
                new FindOrCreateResult<>(2L, true),
                new FindOrCreateResult<>(1L, false)));

        IngestRowBatchProcessor.BatchResult result = process(List.of(
                new ImportedRow(7, row("Anual")),
                new ImportedRow(8, row("Anual")),
                new ImportedRow(9, row("Anual"))), 2026);

        assertThat(result.rowWarnings()).extracting(RowIssueDto::row).containsExactly(7, 8, 9, 9);
        assertThat(result.rowWarnings().get(0).message()).contains("mismo evento");
        assertThat(result.rowWarnings().get(1).message()).contains("Edificio Real");
        assertThat(result.rowWarnings().get(2).message()).contains("Edificio Real");
        assertThat(result.rowWarnings().get(3).message()).contains("mismo evento");
        assertThat(result.pendingAllocations()).extracting(pa -> pa.target())
                .containsExactly(new AllocationTarget.Event(2L));
    }

    @Test
    @DisplayName("lote vacío tras saltear todas las filas: no llama al bulk")
    void loteVacioNoLlamaAlBulk() {
        when(rowResolver.resolveRefs(any(), any(), anyInt(), any(), any(), any(), any()))
                .thenThrow(ResourceNotFoundException.of("Subject", 999));

        IngestRowBatchProcessor.BatchResult result = process(List.of(new ImportedRow(7, row("Anual"))), 2026);

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

        process(List.of(new ImportedRow(7, row("1 Cuat."))), 2026);

        ArgumentCaptor<TermType> termTypeCaptor = ArgumentCaptor.forClass(TermType.class);
        verify(rowResolver).resolveRefs(any(), termTypeCaptor.capture(), anyInt(), any(), any(), any(), any());
        assertThat(termTypeCaptor.getValue()).isEqualTo(TermType.PRIMER_CUATRIMESTRE);
    }
}
