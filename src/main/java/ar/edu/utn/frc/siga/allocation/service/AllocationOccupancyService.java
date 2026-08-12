package ar.edu.utn.frc.siga.allocation.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.modulith.NamedInterface;

import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;

@NamedInterface("api")
public interface AllocationOccupancyService {

    List<OccupiedSlot> findOccupancy(LocalDate from, LocalDate to);
}
