package ar.edu.utn.frc.siga.allocation.service.impl;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import ar.edu.utn.frc.siga.allocation.service.AllocationOccupancyService;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import lombok.RequiredArgsConstructor;

/** Delega en {@link AllocationOccupancyReader}, ya usado internamente para el mismo cálculo. */
@Service
@RequiredArgsConstructor
public class AllocationOccupancyServiceImpl implements AllocationOccupancyService {

    private final AllocationOccupancyReader occupancyReader;

    @Override
    public List<OccupiedSlot> findOccupancy(LocalDate from, LocalDate to) {
        return occupancyReader.loadAssigned(from, to);
    }
}
