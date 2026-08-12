package ar.edu.utn.frc.siga.excelimport.controller;

import ar.edu.utn.frc.siga.excelimport.dto.ImportResultDto;
import ar.edu.utn.frc.siga.excelimport.service.ExcelImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/excelimports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUBSECRETARIA')")
public class ExcelImportController {

    private final ExcelImportService excelImportService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultDto> importExcel(
            @RequestParam("file") MultipartFile file) {
        log.debug("POST /v1/excelimports: filename={}, size={}", file.getOriginalFilename(), file.getSize());
        ImportResultDto result = excelImportService.importExcel(file);
        log.info("Importación Excel completada: filename={}", file.getOriginalFilename());
        return ResponseEntity.ok(result);
    }
}
