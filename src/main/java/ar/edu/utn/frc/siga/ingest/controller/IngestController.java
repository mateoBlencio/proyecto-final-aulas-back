package ar.edu.utn.frc.siga.ingest.controller;

import ar.edu.utn.frc.siga.ingest.dto.IngestResultDto;
import ar.edu.utn.frc.siga.ingest.service.IngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Importación masiva", description = "Carga masiva de horarios/comisiones desde un archivo")
@PreAuthorize("hasRole('SUBSECRETARIA')")
public class IngestController {

    private final IngestService ingestService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importar archivo de horarios",
               description = "Parsea el archivo, resuelve cada fila contra el catálogo académico y de espacios, "
                       + "crea eventos recurrentes y asigna aulas. Filas cuyo catálogo no resuelve se saltean y "
                       + "se reportan, no abortan el import. 400 si el formato del archivo no es soportado o no "
                       + "cumple la plantilla esperada. 422 si una fila tiene datos inválidos (p. ej. día o "
                       + "dictado desconocido, columna requerida vacía) — aborta el import completo.")
    public ResponseEntity<IngestResultDto> ingestFile(
            @RequestParam("file") MultipartFile file) {
        log.debug("POST /v1/excelimports: filename={}, size={}", file.getOriginalFilename(), file.getSize());
        IngestResultDto result = ingestService.ingestFile(file);
        log.info("Importación completada: filename={}", file.getOriginalFilename());
        return ResponseEntity.ok(result);
    }
}
