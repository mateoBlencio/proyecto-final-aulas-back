package ar.edu.utn.frc.siga.excelimport.service;

import ar.edu.utn.frc.siga.excelimport.dto.ImportResultDto;
import org.springframework.web.multipart.MultipartFile;

public interface ExcelImportService {

    ImportResultDto importExcel(MultipartFile file);
}
