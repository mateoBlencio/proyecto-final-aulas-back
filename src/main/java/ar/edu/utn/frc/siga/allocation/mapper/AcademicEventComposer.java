package ar.edu.utn.frc.siga.allocation.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.model.RecurringEvent;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compone el DTO de un evento académico resolviendo materia y comisión (para los
 * recurrentes) vía la fachada de academic. El fetch de datos ajenos vive acá para que
 * {@link AcademicEventMapper} sea un mapper puro sobre la entidad. La variante por lote
 * prefetchea materias y comisiones en dos queries para evitar N+1 en listas.
 */
@Component
@RequiredArgsConstructor
public class AcademicEventComposer {

    private final AcademicEventMapper mapper;
    private final SubjectService subjectService;
    private final CommissionService commissionService;

    /** Composición de un único evento (2 queries si es recurrente). */
    public AcademicEventResponseDto compose(AcademicEvent event) {
        AcademicEvent realEvent = (AcademicEvent) Hibernate.unproxy(event);
        if (realEvent instanceof RecurringEvent r) {
            SubjectResponseDto subject = r.getSubject() != null
                    ? subjectService.findDtoById(r.getSubject().getId()) : null;
            CommissionResponseDto commission = r.getCommission() != null
                    ? commissionService.findDtoById(r.getCommission().getId()) : null;
            return mapper.toDto(realEvent, subject, commission);
        }
        return mapper.toDto(realEvent, null, null);
    }

    /** Composición por lote: prefetch de materias/comisiones distintas, sin N+1. */
    public List<AcademicEventResponseDto> compose(Collection<? extends AcademicEvent> events) {
        List<AcademicEvent> realEvents = events.stream()
                .map(e -> (AcademicEvent) Hibernate.unproxy(e))
                .toList();

        Set<Long> subjectIds = new LinkedHashSet<>();
        Set<Long> commissionIds = new LinkedHashSet<>();
        for (AcademicEvent event : realEvents) {
            if (event instanceof RecurringEvent r) {
                if (r.getSubject() != null) {
                    subjectIds.add(r.getSubject().getId());
                }
                if (r.getCommission() != null) {
                    commissionIds.add(r.getCommission().getId());
                }
            }
        }

        Map<Long, SubjectResponseDto> subjectsById = subjectService.findDtosByIds(subjectIds).stream()
                .collect(Collectors.toMap(SubjectResponseDto::getId, s -> s));
        Map<Long, CommissionResponseDto> commissionsById = commissionService.findDtosByIds(commissionIds).stream()
                .collect(Collectors.toMap(CommissionResponseDto::getId, c -> c));

        List<AcademicEventResponseDto> result = new ArrayList<>(realEvents.size());
        for (AcademicEvent event : realEvents) {
            if (event instanceof RecurringEvent r) {
                SubjectResponseDto subject = r.getSubject() != null
                        ? subjectsById.get(r.getSubject().getId()) : null;
                CommissionResponseDto commission = r.getCommission() != null
                        ? commissionsById.get(r.getCommission().getId()) : null;
                result.add(mapper.toDto(event, subject, commission));
            } else {
                result.add(mapper.toDto(event, null, null));
            }
        }
        return result;
    }
}