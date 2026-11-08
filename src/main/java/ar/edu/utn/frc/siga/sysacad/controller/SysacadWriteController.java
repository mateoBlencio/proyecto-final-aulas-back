package ar.edu.utn.frc.siga.sysacad.controller;

import ar.edu.utn.frc.siga.sysacad.api.SysacadWritePreviewItemDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadWriteStatusDto;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.write.RunReport;
import ar.edu.utn.frc.siga.sysacad.internal.write.SysacadWriteJob;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.PlannedSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.write.state.SysacadScheduleQuery;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.SysacadWritePlanner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/sysacad/write")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
@Tag(name = "Escritura hacia SysAcad", description = "Vista previa y estado de los cambios de aula que se envían a SysAcad")
public class SysacadWriteController {

    private final SysacadWritePlanner planner;
    private final SysacadScheduleQuery query;
    private final SysacadWriteJob job;

    @GetMapping("/preview")
    @PreAuthorize("hasAuthority('PERM_SYSACAD_READ')")
    @Operation(summary = "Vista previa de lo que se enviaría a SysAcad",
               description = "Calcula contra la base, sin llamar a SysAcad ni enviar nada, el aula de la próxima "
                       + "clase de cada horario y la compara con el valor conocido. Lista los horarios con algo "
                       + "para enviar, en espera de un pedido sin aceptar, sin aula o con un destino que no se "
                       + "puede enviar, ordenados por la clase más cercana. Omite los que no cambian y los que no "
                       + "tienen más clases.")
    public ResponseEntity<List<SysacadWritePreviewItemDto>> preview() {
        log.debug("GET /v1/sysacad/write/preview");
        return ResponseEntity.ok(planner.plan(LocalDateTime.now()).stream().map(SysacadWriteController::toItem).toList());
    }

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('PERM_SYSACAD_READ')")
    @Operation(summary = "Estado del job de envío a SysAcad",
               description = "Devuelve el modo de escritura, el inicio, el fin y el resultado de la última "
                       + "corrida, la última lectura de la vista, desde cuándo cae la conexión, hasta cuándo está "
                       + "pausado, el último error y la cantidad de horarios en revisión y en espera. Sirve para "
                       + "detectar un job detenido.")
    public ResponseEntity<SysacadWriteStatusDto> status() {
        log.debug("GET /v1/sysacad/write/status");
        return ResponseEntity.ok(query.writeStatus());
    }

    @PostMapping("/run")
    @PreAuthorize("hasAuthority('PERM_SYSACAD_SYNC')")
    @Operation(summary = "Forzar una corrida del job de envío a SysAcad",
               description = "Ejecuta ahora una corrida del job de envío, con el modo de escritura vigente, y "
                       + "devuelve el resultado. Responde 409 si el modo está en OFF o si hay un sync o una "
                       + "corrida de envío en curso.")
    public ResponseEntity<RunReport> run() {
        log.info("POST /v1/sysacad/write/run: corrida manual del job de envío");
        return ResponseEntity.ok(job.runNow());
    }

    private static SysacadWritePreviewItemDto toItem(PlannedSchedule planned) {
        SysacadSchedule s = planned.schedule();
        return switch (planned.decision()) {
            case NextClassDecision.Send d -> item(s, "SEND", d.building(), d.room(), d.occurrenceId(), d.date(),
                    null, null);
            case NextClassDecision.Waiting d -> item(s, "WAITING", null, null, d.occurrenceId(), d.date(),
                    d.itemId(), null);
            case NextClassDecision.NoRoom d -> item(s, "NO_ROOM", null, null, d.occurrenceId(), d.date(),
                    null, null);
            case NextClassDecision.NoRoomAfterCancel d -> item(s, "NO_ROOM_AFTER_CANCEL", null, null,
                    d.occurrenceId(), d.date(), d.itemId(), null);
            case NextClassDecision.NotSendable d -> item(s, "NOT_SENDABLE", null, null, d.occurrenceId(), d.date(),
                    null, d.reason().name());
            case NextClassDecision.Unchanged d -> item(s, "UNCHANGED", null, null, d.occurrenceId(), d.date(),
                    null, null);
            case NextClassDecision.NoNextClass d -> item(s, "NO_NEXT_CLASS", null, null, null, null, null, null);
        };
    }

    private static SysacadWritePreviewItemDto item(SysacadSchedule s, String decision, Integer building,
            Integer room, Long occurrenceId, java.time.LocalDate date, Long itemId, String reason) {
        return new SysacadWritePreviewItemDto(s.getId(), s.getCourseCode(), s.getSubject(), s.getCommission(),
                s.getDayOfWeek(), s.getSemester(), s.getKnownBuilding(), s.getKnownRoom(), decision, building, room,
                occurrenceId, date, itemId, reason);
    }
}
