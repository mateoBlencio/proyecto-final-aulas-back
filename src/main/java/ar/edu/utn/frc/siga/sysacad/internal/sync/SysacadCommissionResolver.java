package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class SysacadCommissionResolver {

    private SysacadCommissionResolver() {
    }

    static Optional<CommissionResponseDto> resolveCommission(CommissionService commissionService,
            Map<String, Optional<CommissionResponseDto>> cache, String courseCode) {
        return cache.computeIfAbsent(courseCode, code -> {
            try {
                return Optional.of(commissionService.findActiveByCourseCode(code));
            } catch (ResourceNotFoundException e) {
                log.warn("No se pudo resolver una comisión vigente para el curso {}: fila salteada", code);
                return Optional.empty();
            }
        });
    }

    static Optional<SubjectCommissionResponseDto> resolveLink(SubjectCommissionService subjectCommissionService,
            Map<LinkKey, Optional<SubjectCommissionResponseDto>> cache, Long commissionId, Integer subjectCode) {
        return cache.computeIfAbsent(new LinkKey(commissionId, subjectCode), key -> {
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
        return switch (semester) {
            case 0 -> List.of(TermType.PRIMER_CUATRIMESTRE, TermType.SEGUNDO_CUATRIMESTRE);
            case 1 -> List.of(TermType.PRIMER_CUATRIMESTRE);
            case 2 -> List.of(TermType.SEGUNDO_CUATRIMESTRE);
            default -> {
                log.warn("HorarioCuatrimestre fuera de rango (0,1,2) para curso={} materia={}: {}",
                        courseCode, subjectCode, semester);
                yield List.of();
            }
        };
    }

    record LinkKey(Long commissionId, Integer subjectCode) {
    }
}
