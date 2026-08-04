package ar.edu.utn.frc.siga.allocation.events.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.allocation.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.events.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.events.model.UniqueEvent;
import ar.edu.utn.frc.siga.common.util.Maps;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compone el DTO de un evento académico resolviendo datos ajenos a la entidad: materia y comisión (ambos subtipos). */
@Component
@RequiredArgsConstructor
public class AcademicEventComposer {

    private final AcademicEventMapper mapper;
    private final SubjectService subjectService;
    private final CommissionService commissionService;

    /** Composición de un único evento (delega en el batch con una lista de un elemento). */
    public AcademicEventResponseDto compose(AcademicEvent event) {
        return compose(List.of(event)).getFirst();
    }

    /** Composición por lote indexada por id de evento, para lookups posteriores por id. */
    public Map<Long, AcademicEventResponseDto> composeById(List<? extends AcademicEvent> events) {
        List<AcademicEventResponseDto> composed = compose(events);
        Map<Long, AcademicEventResponseDto> byId = new LinkedHashMap<>();
        for (int i = 0; i < events.size(); i++) {
            byId.put(events.get(i).getId(), composed.get(i));
        }
        return byId;
    }

    /** Composición por lote: prefetch de materias/comisiones distintas, sin N+1. */
    public List<AcademicEventResponseDto> compose(Collection<? extends AcademicEvent> events) {
        List<AcademicEvent> realEvents = events.stream()
                .map(e -> (AcademicEvent) Hibernate.unproxy(e))
                .toList();

        Set<Long> subjectIds = new LinkedHashSet<>();
        Set<Long> commissionIds = new LinkedHashSet<>();
        for (AcademicEvent event : realEvents) {
            if (event.getSubjectId() != null) {
                subjectIds.add(event.getSubjectId());
            }
            if (event.getCommissionId() != null) {
                commissionIds.add(event.getCommissionId());
            }
        }

        Map<Long, SubjectResponseDto> subjectsById = Maps.byId(subjectService.findByIds(subjectIds), SubjectResponseDto::id);
        Map<Long, CommissionResponseDto> commissionsById = Maps.byId(commissionService.findByIds(commissionIds), CommissionResponseDto::id);

        List<AcademicEventResponseDto> result = new ArrayList<>(realEvents.size());
        for (AcademicEvent event : realEvents) {
            SubjectResponseDto subject = event.getSubjectId() != null ? subjectsById.get(event.getSubjectId()) : null;
            CommissionResponseDto commission = event.getCommissionId() != null ? commissionsById.get(event.getCommissionId()) : null;
            if (event instanceof RecurringEvent r) {
                result.add(mapper.toDto(r, subject, commission));
            } else {
                result.add(mapper.toDto((UniqueEvent) event, subject, commission));
            }
        }
        return result;
    }
}
