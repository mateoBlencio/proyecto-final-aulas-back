package ar.edu.utn.frc.siga.roomrequest.controller;

import ar.edu.utn.frc.siga.roomrequest.dto.response.ClassroomOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.CommissionOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.CommissionScheduleDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.SpecialtyOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.SubjectOptionDto;
import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Combos públicos del formulario; evita abrir los controllers internos de {@code academic}/{@code space} (cerrados por rol). */
@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/room-requests/catalog")
@RequiredArgsConstructor
@Tag(name = "Solicitudes de aula — catálogos",
     description = "Datos públicos para poblar los combos del formulario de solicitud")
public class RoomRequestCatalogController {

    private final RoomRequestCatalogService catalogService;

    @GetMapping("/specialties")
    @Operation(summary = "Listar especialidades",
               description = "Primer combo del formulario. Devuelve código y nombre.")
    public ResponseEntity<List<SpecialtyOptionDto>> findSpecialties() {
        log.debug("GET /v1/room-requests/catalog/specialties");
        return ResponseEntity.ok(catalogService.findSpecialties());
    }

    @GetMapping("/subjects")
    @Operation(summary = "Listar materias de una especialidad",
               description = "Segundo combo del formulario, filtrado por el código de especialidad elegido.")
    public ResponseEntity<List<SubjectOptionDto>> findSubjects(@RequestParam Integer specialtyCode) {
        log.debug("GET /v1/room-requests/catalog/subjects: specialtyCode={}", specialtyCode);
        return ResponseEntity.ok(catalogService.findSubjectsBySpecialty(specialtyCode));
    }

    @GetMapping("/commissions")
    @Operation(summary = "Listar comisiones de una materia",
               description = "Tercer combo del formulario, filtrado por la materia elegida.")
    public ResponseEntity<List<CommissionOptionDto>> findCommissions(@RequestParam Long subjectId) {
        log.debug("GET /v1/room-requests/catalog/commissions: subjectId={}", subjectId);
        return ResponseEntity.ok(catalogService.findCommissionsBySubject(subjectId));
    }

    @GetMapping("/classrooms")
    @Operation(summary = "Listar aulas disponibles",
               description = "Para elegir las aulas de preferencia. Devuelve solo identificación y edificio.")
    public ResponseEntity<List<ClassroomOptionDto>> findClassrooms() {
        log.debug("GET /v1/room-requests/catalog/classrooms");
        return ResponseEntity.ok(catalogService.findClassrooms());
    }

    @GetMapping("/commission-schedule")
    @Operation(summary = "Días y horarios de cursado de una comisión",
               description = "Slots de cursado (día + horario) y fechas de cursado futuras de la comisión, "
                       + "para el calendario y los checkboxes de día del formulario. 404 si la comisión no "
                       + "pertenece a la materia.")
    public ResponseEntity<CommissionScheduleDto> findCommissionSchedule(
            @RequestParam Long subjectId, @RequestParam Long commissionId) {
        log.debug("GET /v1/room-requests/catalog/commission-schedule: subjectId={}, commissionId={}",
                subjectId, commissionId);
        return ResponseEntity.ok(catalogService.findCommissionSchedule(subjectId, commissionId));
    }
}
