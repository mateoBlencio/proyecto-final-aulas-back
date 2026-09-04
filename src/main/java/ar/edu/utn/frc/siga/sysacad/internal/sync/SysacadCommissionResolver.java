package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Resuelve la comisión vigente y su link materia-comisión para las filas de SysAcad, con caché por
 * corrida de sync (una instancia por {@code sync()}). Concentra la resolución que EVENTOS y ASIGNACIONES
 * hacían de forma idéntica, junto con las dos cachés que antes cada syncer declaraba a mano.
 */
@Slf4j
final class SysacadCommissionResolver {

    private final CommissionService commissionService;
    private final SubjectCommissionService subjectCommissionService;
    private final Map<String, Optional<CommissionResponseDto>> commissionCache = new HashMap<>();
    private final Map<LinkKey, Optional<SubjectCommissionResponseDto>> linkCache = new HashMap<>();

    SysacadCommissionResolver(CommissionService commissionService,
            SubjectCommissionService subjectCommissionService) {
        this.commissionService = commissionService;
        this.subjectCommissionService = subjectCommissionService;
    }

    /**
     * Devuelve vacío (y ya logueó el WARN correspondiente) si falta la comisión vigente o el link, así el
     * syncer solo hace {@code if (resolved.isEmpty()) continue;}.
     */
    Optional<ResolvedLink> resolve(String courseCode, Integer subjectCode) {
        Optional<CommissionResponseDto> commission = resolveCommission(courseCode);
        if (commission.isEmpty()) {
            return Optional.empty();
        }
        Optional<SubjectCommissionResponseDto> link = resolveLink(commission.get().id(), subjectCode);
        return link.map(l -> new ResolvedLink(commission.get(), l));
    }

    private Optional<CommissionResponseDto> resolveCommission(String courseCode) {
        return commissionCache.computeIfAbsent(courseCode, code -> {
            try {
                return Optional.of(commissionService.findActiveByCourseCode(code));
            } catch (ResourceNotFoundException e) {
                log.warn("No se pudo resolver una comisión vigente para el curso {}: fila salteada", code);
                return Optional.empty();
            }
        });
    }

    private Optional<SubjectCommissionResponseDto> resolveLink(Long commissionId, Integer subjectCode) {
        return linkCache.computeIfAbsent(new LinkKey(commissionId, subjectCode), key -> {
            try {
                return Optional.of(subjectCommissionService.findByCommissionAndSubjectCode(
                        key.commissionId(), key.subjectCode()));
            } catch (ResourceNotFoundException e) {
                log.warn("No se pudo resolver el link materia-comisión: comisión={}, materia={}: fila salteada",
                        key.commissionId(), key.subjectCode());
                return Optional.empty();
            }
        });
    }

    static List<TermType> termTypes(Integer semester, String courseCode, Integer subjectCode) {
        if (semester == null) {
            log.warn("HorarioCuatrimestre nulo para curso={} materia={}: fila salteada", courseCode, subjectCode);
            return List.of();
        }
        if (semester == 0) {
            return List.of(TermType.PRIMER_CUATRIMESTRE, TermType.SEGUNDO_CUATRIMESTRE);
        }
        return TermType.fromSemester(semester)
                .map(List::of)
                .orElseGet(() -> {
                    log.warn("HorarioCuatrimestre fuera de rango (0,1,2) para curso={} materia={}: {}",
                            courseCode, subjectCode, semester);
                    return List.of();
                });
    }

    record LinkKey(Long commissionId, Integer subjectCode) {
    }

    record ResolvedLink(CommissionResponseDto commission, SubjectCommissionResponseDto link) {
    }
}
