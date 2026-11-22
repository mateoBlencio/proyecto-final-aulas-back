package ar.edu.utn.frc.siga.academic.controller;

import ar.edu.utn.frc.siga.academic.dto.response.HolidayResponseDto;
import ar.edu.utn.frc.siga.academic.service.HolidayService;
import ar.edu.utn.frc.siga.common.util.DateRanges;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/holidays")
@RequiredArgsConstructor
@Tag(name = "Feriados", description = "Consulta de feriados sincronizados desde SysAcad")
@PreAuthorize("hasAuthority('PERM_ACADEMIC_READ')")
public class HolidayController {

    private final HolidayService holidayService;

    @GetMapping
    @Operation(summary = "Listar feriados por rango",
               description = "Feriados con fecha entre from y to (inclusive), ordenados por fecha. "
                       + "Ambos parámetros son obligatorios (yyyy-MM-dd) y to no puede ser anterior a from.")
    public ResponseEntity<List<HolidayResponseDto>> findBetween(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.debug("GET /v1/holidays?from={}&to={}", from, to);
        DateRanges.requireNotBefore(to, from);
        return ResponseEntity.ok(holidayService.findBetween(from, to));
    }
}
