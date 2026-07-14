package ar.edu.utn.frc.siga.excelimport.service;

import ar.edu.utn.frc.siga.excelimport.dto.ImportResultDto;
import org.springframework.web.multipart.MultipartFile;

/**
 * Importación masiva de la oferta académica (comisiones, eventos y asignaciones de aula)
 * a partir de un archivo Excel con la planilla institucional.
 */
public interface ExcelImportService {

    /**
     * Valida y procesa el archivo Excel completo, fila por fila, creando o reutilizando
     * las entidades académicas y de espacios físicos involucradas y sus asignaciones.
     */
    ImportResultDto importExcel(MultipartFile file);
}
