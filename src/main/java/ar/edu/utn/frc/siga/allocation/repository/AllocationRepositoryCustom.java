package ar.edu.utn.frc.siga.allocation.repository;

import ar.edu.utn.frc.siga.allocation.model.Allocation;

import java.util.Collection;
import java.util.List;

public interface AllocationRepositoryCustom {

    List<Allocation> findByOccurrenceIdIn(Collection<Long> occurrenceIds);
}
