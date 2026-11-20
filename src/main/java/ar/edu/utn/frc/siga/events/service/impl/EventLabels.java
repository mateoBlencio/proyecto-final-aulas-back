package ar.edu.utn.frc.siga.events.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.AcademicLabels;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.events.model.AcademicEvent;
import ar.edu.utn.frc.siga.events.model.UniqueEvent;
import ar.edu.utn.frc.siga.events.repository.AcademicEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Audit label of academic events, shared by the event and occurrence providers. */
@Component
@RequiredArgsConstructor
class EventLabels {

    private final SubjectService subjectService;
    private final CommissionService commissionService;
    private final AcademicEventRepository eventRepository;

    /** Fields of an event that make up its label. */
    record EventRef(Long subjectId, Long commissionId, String description) {
    }

    Map<Long, String> labelsOfEventIds(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, EventRef> refs = new HashMap<>();
        for (AcademicEvent event : eventRepository.findAllById(eventIds)) {
            String description = event instanceof UniqueEvent unique ? unique.getDescription() : null;
            refs.put(event.getId(), new EventRef(event.getSubjectId(), event.getCommissionId(), description));
        }
        return labelsOf(refs);
    }

    /**
     * Label of each event: "{subject} ({commission})"; a unique event without subject falls back to its description.
     * One subject lookup and one commission lookup for all the events. Subjects of deactivated records are included.
     */
    Map<Long, String> labelsOf(Map<Long, EventRef> refsByEventId) {
        Set<Long> subjectIds = new HashSet<>();
        Set<Long> commissionIds = new HashSet<>();
        refsByEventId.values().forEach(ref -> {
            if (ref.subjectId() != null) {
                subjectIds.add(ref.subjectId());
            }
            if (ref.commissionId() != null) {
                commissionIds.add(ref.commissionId());
            }
        });
        Map<Long, SubjectResponseDto> subjects = subjectIds.isEmpty() ? new HashMap<>()
                : Maps.byId(subjectService.findByIdsIncludingDeactivated(subjectIds), SubjectResponseDto::id);
        Map<Long, CommissionResponseDto> commissions = commissionIds.isEmpty() ? new HashMap<>()
                : Maps.byId(commissionService.findByIdsIncludingDeactivated(commissionIds), CommissionResponseDto::id);

        Map<Long, String> labels = new HashMap<>();
        refsByEventId.forEach((eventId, ref) -> {
            SubjectResponseDto subject = subjects.get(ref.subjectId());
            CommissionResponseDto commission = commissions.get(ref.commissionId());
            String label;
            if (subject != null) {
                label = AcademicLabels.subjectCommission(subject.name(),
                        commission == null ? null : commission.courseCode());
            } else if (ref.description() != null && !ref.description().isBlank()) {
                label = ref.description();
            } else {
                label = commission == null ? null : commission.courseCode();
            }
            if (label != null) {
                labels.put(eventId, label);
            }
        });
        return labels;
    }
}
