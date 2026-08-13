package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationConflictDto;
import ar.edu.utn.frc.siga.allocation.model.ConflictType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.NamedInterface;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@NamedInterface("api")
public interface AllocationConflictService {

    Page<AllocationConflictDto> findConflicts(Set<ConflictType> types, LocalDate from, LocalDate to, boolean includePast, Pageable pageable);
    List<Long> resolveAllUnallocatedEventIds();
}
