package ar.edu.utn.frc.classroom_allocation.excelimport.controller;

import ar.edu.utn.frc.classroom_allocation.excelimport.dto.ImportResultDto;
import ar.edu.utn.frc.classroom_allocation.excelimport.service.ExcelImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/excelimports")
@RequiredArgsConstructor
public class ExcelImportController {

    private final ExcelImportService excelImportService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultDto> importExcel(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(excelImportService.importExcel(file));
    }
}
