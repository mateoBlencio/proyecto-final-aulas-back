package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.common.service.OccurrenceAllocationQuery;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class OccurrenceAllocationQueryImpl implements OccurrenceAllocationQuery {

    private final AllocationRepository allocationRepository;

    @Override
    @Transactional(readOnly = true)
    public Set<Long> allocatedAmong(Collection<Long> occurrenceIds) {
        if (occurrenceIds == null || occurrenceIds.isEmpty()) {
            return Set.of();
        }
        return allocationRepository.findByOccurrenceIdIn(occurrenceIds).stream()
                .map(Allocation::getOccurrenceId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
