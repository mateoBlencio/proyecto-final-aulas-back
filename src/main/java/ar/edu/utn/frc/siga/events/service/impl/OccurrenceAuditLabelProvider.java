package ar.edu.utn.frc.siga.events.service.impl;

import ar.edu.utn.frc.siga.audit.AuditLabelProvider;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.events.model.AcademicEvent;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.UniqueEvent;
import ar.edu.utn.frc.siga.events.repository.AcademicEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Label of an occurrence: "Álgebra 1K1 · 04/03/2026". If the event no longer exists, only the date. */
@Component
@RequiredArgsConstructor
class OccurrenceAuditLabelProvider implements AuditLabelProvider {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AcademicEventRepository eventRepository;
    private final EventLabels eventLabels;

    @Override
    public Class<?> entityType() {
        return Occurrence.class;
    }

    @Override
    public Map<String, String> labels(List<AuditedRecord> records) {
        Set<Long> eventIds = records.stream().map(record -> record.longValue("event"))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, EventLabels.EventRef> refs = new HashMap<>();
        for (AcademicEvent event : eventRepository.findAllById(eventIds)) {
            String description = event instanceof UniqueEvent unique ? unique.getDescription() : null;
            refs.put(event.getId(), new EventLabels.EventRef(event.getSubjectId(), event.getCommissionId(), description));
        }
        Map<Long, String> eventLabelsById = eventLabels.labelsOf(refs);

        Map<String, String> labels = new HashMap<>();
        for (AuditedRecord record : records) {
            String date = record.values().get("date") instanceof LocalDate d ? d.format(DATE) : null;
            String eventLabel = eventLabelsById.get(record.longValue("event"));
            String label = eventLabel == null ? date : date == null ? eventLabel : eventLabel + " · " + date;
            if (label != null) {
                labels.put(record.recordId(), label);
            }
        }
        return labels;
    }
}
