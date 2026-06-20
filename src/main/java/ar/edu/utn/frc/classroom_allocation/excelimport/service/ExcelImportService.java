package ar.edu.utn.frc.classroom_allocation.excelimport.service;

import ar.edu.utn.frc.classroom_allocation.excelimport.dto.ImportResultDto;
import org.springframework.web.multipart.MultipartFile;

public interface ExcelImportService {

    ImportResultDto importExcel(MultipartFile file);
}
