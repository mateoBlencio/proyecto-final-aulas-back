package ar.edu.utn.frc.siga.ingest.service.impl;

import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.ingest.dto.IngestResultDto;
import ar.edu.utn.frc.siga.ingest.exception.InvalidFileFormatException;
import ar.edu.utn.frc.siga.ingest.source.IngestSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * IngestServiceImpl con fuentes falsas: aísla la selección de {@link IngestSource} (Strategy)
 * de los detalles de Excel, ya cubiertos por IngestServiceImplTest.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IngestServiceImpl: selección de fuente")
class IngestSourceSelectionTest {

    @Mock
    private IngestSource excelSource;
    @Mock
    private IngestSource csvSource;
    @Mock
    private IngestRowBatchProcessor batchProcessor;
    @Mock
    private AllocationService allocationService;

    private IngestServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(excelSource.supports(any())).thenReturn(false);
        lenient().when(csvSource.supports(any())).thenReturn(false);
        service = new IngestServiceImpl(List.of(excelSource, csvSource), batchProcessor, allocationService);
    }

    @Test
    @DisplayName("elige la fuente cuyo supports() da true, aunque no sea la primera de la lista")
    void eligeLaFuenteQueSoporta() {
        MockMultipartFile file = new MockMultipartFile("file", "planilla.csv", "text/csv", new byte[0]);
        when(csvSource.supports("planilla.csv")).thenReturn(true);
        when(csvSource.parse(file)).thenReturn(new IngestSource.ParsedContent(List.of(), 2026));
        when(batchProcessor.process(any(), eq(2026))).thenReturn(
                new IngestRowBatchProcessor.BatchResult(0, 0, 0, List.of(), List.of(), List.of()));

        IngestResultDto result = service.ingestFile(file);

        assertThat(result.processedRows()).isZero();
        org.mockito.Mockito.verify(excelSource, org.mockito.Mockito.never()).parse(any());
    }

    @Test
    @DisplayName("ninguna fuente soporta el archivo → InvalidFileFormatException")
    void ningunaFuenteSoporta() {
        MockMultipartFile file = new MockMultipartFile("file", "planilla.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.ingestFile(file)).isInstanceOf(InvalidFileFormatException.class);
        verifyNoInteractions(batchProcessor);
    }

    @Test
    @DisplayName("sin filas parseadas → no llama a allocationService")
    void sinFilasNoLlamaAAllocationService() {
        MockMultipartFile file = new MockMultipartFile("file", "vacia.xlsx", "application/vnd.ms-excel", new byte[0]);
        when(excelSource.supports("vacia.xlsx")).thenReturn(true);
        when(excelSource.parse(file)).thenReturn(new IngestSource.ParsedContent(List.of(), 2026));
        when(batchProcessor.process(any(), eq(2026))).thenReturn(
                new IngestRowBatchProcessor.BatchResult(0, 0, 0, List.of(), List.of(), List.of()));

        service.ingestFile(file);

        verifyNoInteractions(allocationService);
    }
}
