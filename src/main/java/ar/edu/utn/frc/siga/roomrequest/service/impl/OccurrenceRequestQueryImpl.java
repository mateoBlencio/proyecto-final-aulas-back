package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.roomrequest.dto.response.OccurrenceRequestStateDto;
import ar.edu.utn.frc.siga.roomrequest.model.OccurrenceRequestState;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemAllocationRepository;
import ar.edu.utn.frc.siga.roomrequest.service.OccurrenceRequestQuery;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OccurrenceRequestQueryImpl implements OccurrenceRequestQuery {

    private final RoomRequestItemAllocationRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, OccurrenceRequestStateDto> findStates(Collection<Long> occurrenceIds) {
        if (occurrenceIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, OccurrenceRequestStateDto> result = new HashMap<>();
        for (RoomRequestItemAllocationRepository.OccurrenceItemStatus row : repository.findItemStatuses(occurrenceIds)) {
            OccurrenceRequestStateDto candidate =
                    new OccurrenceRequestStateDto(row.occurrenceId(), row.itemId(), toState(row.status()));
            result.merge(row.occurrenceId(), candidate,
                    (current, next) -> next.state().compareTo(current.state()) < 0 ? next : current);
        }
        return result;
    }

    private static OccurrenceRequestState toState(RoomRequestStatus status) {
        return switch (status) {
            case RESOLVED -> OccurrenceRequestState.ACCEPTED;
            case CANCELLED -> OccurrenceRequestState.CANCELLED;
            default -> OccurrenceRequestState.PENDING;
        };
    }
}
