package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.DeallocatedOccurrenceDto;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;

import java.time.LocalDate;
import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface AllocationService {

    AllocationResponseDto findById(Long allocationId);

    List<AllocationResponseDto> findByDate(LocalDate date);

    List<AllocationResponseDto> allocate(AllocationCommand command);

    List<AllocationResponseDto> reallocate(AllocationCommand command);

    List<DeallocatedOccurrenceDto> deallocate(DeallocationCommand command);
}
