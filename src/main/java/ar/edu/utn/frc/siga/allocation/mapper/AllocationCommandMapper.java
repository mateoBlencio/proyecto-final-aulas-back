package ar.edu.utn.frc.siga.allocation.mapper;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import ar.edu.utn.frc.siga.allocation.dto.request.AllocationBatchRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationItemRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.DeallocationBatchRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.DeallocationTargetRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.ReleaseBatchRequestDto;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.ReleaseCommand;

@Component
public class AllocationCommandMapper {

    public AllocationCommand toManualCommand(AllocationBatchRequestDto dto) {
        List<AllocationItem> items = dto.items().stream().map(this::toItem).toList();
        return AllocationCommand.manual(items, dto.observation());
    }

    public DeallocationCommand toDeallocationCommand(DeallocationBatchRequestDto dto) {
        List<AllocationTarget> targets = dto.items().stream().map(this::toTarget).toList();
        return new DeallocationCommand(targets, dto.observation());
    }

    public ReleaseCommand toReleaseCommand(ReleaseBatchRequestDto dto) {
        List<AllocationTarget> targets = dto.items().stream().map(this::toTarget).toList();
        return new ReleaseCommand(targets, dto.reason(), dto.note());
    }

    private AllocationItem toItem(AllocationItemRequestDto dto) {
        return new AllocationItem(
                toTarget(dto.occurrenceIds(), dto.eventId(), dto.from(), dto.to()), dto.classroomId());
    }

    private AllocationTarget toTarget(DeallocationTargetRequestDto dto) {
        return toTarget(dto.occurrenceIds(), dto.eventId(), dto.from(), dto.to());
    }

    // Las combinaciones inválidas (rango sin evento, `to` sin `from`) no llegan hasta acá: las
    // corta Bean Validation en los DTOs de request con un 400.
    private AllocationTarget toTarget(List<Long> occurrenceIds, Long eventId, LocalDate from, LocalDate to) {
        if (eventId == null) {
            return new AllocationTarget.Occurrences(occurrenceIds);
        }
        return from == null
                ? new AllocationTarget.Event(eventId)
                : new AllocationTarget.EventRange(eventId, from, to);
    }
}
