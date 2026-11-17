package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.common.service.OccurrenceRequestReferenceQuery;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemAllocationRepository;
import java.util.Collection;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class OccurrenceRequestReferenceQueryImpl implements OccurrenceRequestReferenceQuery {

    private final RoomRequestItemAllocationRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Set<Long> referencedAmong(Collection<Long> occurrenceIds) {
        if (occurrenceIds == null || occurrenceIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(repository.findReferencedOccurrenceIds(occurrenceIds));
    }
}
