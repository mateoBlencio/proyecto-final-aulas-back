package ar.edu.utn.frc.siga.events.service.impl;

import ar.edu.utn.frc.siga.events.dto.response.EventHistorySnapshotDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceHistorySnapshotDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventHistorySnapshotDto;
import ar.edu.utn.frc.siga.events.dto.response.UniqueEventHistorySnapshotDto;
import ar.edu.utn.frc.siga.events.model.AcademicEvent;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.model.UniqueEvent;
import ar.edu.utn.frc.siga.events.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.EventAuditHistoryService;
import ar.edu.utn.frc.siga.audit.RevisionDto;
import ar.edu.utn.frc.siga.audit.RevisionReader;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventAuditHistoryServiceImpl implements EventAuditHistoryService {

    private final RevisionReader revisionReader;
    private final AcademicEventRepository eventRepository;
    private final OccurrenceRepository occurrenceRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RevisionDto<EventHistorySnapshotDto>> findEventHistory(Long eventId) {
        List<RevisionDto<EventHistorySnapshotDto>> revisions =
                revisionReader.readById(AcademicEvent.class, eventId,
                        entity -> toEventSnapshot((AcademicEvent) entity));
        if (revisions.isEmpty() && !eventRepository.existsById(eventId)) {
            throw ResourceNotFoundException.of("AcademicEvent", eventId);
        }
        return revisions;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RevisionDto<OccurrenceHistorySnapshotDto>> findOccurrenceHistory(Long occurrenceId) {
        List<RevisionDto<OccurrenceHistorySnapshotDto>> revisions =
                revisionReader.readById(Occurrence.class, occurrenceId,
                        entity -> toOccurrenceSnapshot((Occurrence) entity));
        if (revisions.isEmpty() && !occurrenceRepository.existsById(occurrenceId)) {
            throw ResourceNotFoundException.of("Occurrence", occurrenceId);
        }
        return revisions;
    }

    private OccurrenceHistorySnapshotDto toOccurrenceSnapshot(Occurrence occurrence) {
        return new OccurrenceHistorySnapshotDto(
                occurrence.getId(),
                occurrence.getEvent().getId(),
                occurrence.getDate(),
                occurrence.getStatus());
    }

    private EventHistorySnapshotDto toEventSnapshot(AcademicEvent event) {
        return switch (event) {
            case RecurringEvent recurring -> new RecurringEventHistorySnapshotDto(
                    recurring.getId(), recurring.getType(), recurring.getEnrolled(), recurring.getStartTime(),
                    recurring.getDuration().toMinutes(), recurring.getDayOfWeek(), recurring.getStartDate(),
                    recurring.getEndDate(), recurring.getSubjectId(), recurring.getCommissionId());
            case UniqueEvent unique -> new UniqueEventHistorySnapshotDto(
                    unique.getId(), unique.getType(), unique.getEnrolled(), unique.getStartTime(),
                    unique.getDuration().toMinutes(), unique.getDate(), unique.getDescription(),
                    unique.getKind(), unique.getSubjectId(), unique.getCommissionId());
            default -> throw new IllegalStateException("Subtipo de evento no soportado: " + event.getClass());
        };
    }
}
