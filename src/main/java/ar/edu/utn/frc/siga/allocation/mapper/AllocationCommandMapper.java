package ar.edu.utn.frc.siga.allocation.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.edu.utn.frc.siga.allocation.dto.request.AllocationBatchRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationItemRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.DeallocationBatchRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.DeallocationTargetRequestDto;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;

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

    private AllocationItem toItem(AllocationItemRequestDto dto) {
        return new AllocationItem(toTarget(dto.occurrenceIds(), dto.eventId()), dto.classroomId());
    }

    private AllocationTarget toTarget(DeallocationTargetRequestDto dto) {
        return toTarget(dto.occurrenceIds(), dto.eventId());
    }

    private AllocationTarget toTarget(List<Long> occurrenceIds, Long eventId) {
        return eventId != null
                ? new AllocationTarget.Event(eventId)
                : new AllocationTarget.Occurrences(occurrenceIds);
    }
}
