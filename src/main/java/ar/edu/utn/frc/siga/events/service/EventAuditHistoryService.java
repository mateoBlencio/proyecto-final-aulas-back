package ar.edu.utn.frc.siga.events.service;

import ar.edu.utn.frc.siga.events.dto.response.EventHistorySnapshotDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceHistorySnapshotDto;
import ar.edu.utn.frc.siga.common.dto.response.RevisionDto;

import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface EventAuditHistoryService {

    List<RevisionDto<EventHistorySnapshotDto>> findEventHistory(Long eventId);

    List<RevisionDto<OccurrenceHistorySnapshotDto>> findOccurrenceHistory(Long occurrenceId);
}
