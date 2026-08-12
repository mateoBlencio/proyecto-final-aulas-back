package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.common.util.Maps;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Lectura de ocupación (occurrences con {@code Allocation}) compartida por detección de problemas y carga de auto-asignación. */
@Component
@RequiredArgsConstructor
class AllocationOccupancyReader {

    private final OccurrenceService occurrenceService;
    private final AllocationRepository allocationRepository;

    List<OccupiedSlot> loadAssigned(LocalDate from, LocalDate to) {
        Map<Long, OccurrenceSlotDto> slotByOccurrenceId = Maps.byId(
                occurrenceService.findSlotsBetween(from, to),
                OccurrenceSlotDto::occurrenceId);
        return allocationRepository.findByOccurrenceIdIn(slotByOccurrenceId.keySet()).stream()
                .map(a -> OccupiedSlot.from(a, slotByOccurrenceId.get(a.getOccurrenceId())))
                .toList();
    }
}
